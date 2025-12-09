/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * You may find it more helpful to your design to adjust the
 * functionality, constants and interfaces (if there are any)
 * provided within in order to meet the needs of your specific
 * Programming the Internet of Things project.
 */

package programmingtheiot.gda.app;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.BaseIotData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.data.SystemStateData;
import programmingtheiot.gda.connection.CloudClientConnector;
import programmingtheiot.gda.connection.CoapClientConnector;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.ICloudClient;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;
import programmingtheiot.gda.system.SystemPerformanceManager;
import redis.clients.jedis.JedisPubSub;

/**
 * Main data manager for the Gateway Device Application.
 * Extends JedisPubSub to handle Redis Pub/Sub messages from CDA.
 *
 */
public class DeviceDataManager extends JedisPubSub implements IDataMessageListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(DeviceDataManager.class.getName());
	
	// private var's
	
	private boolean enableMqttClient = false;
	private boolean enableCoapServer = false;
	private boolean enableCoapClient = false;
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	private boolean enableSystemPerf = false;
	
	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private ICloudClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private IRequestResponseClient coapClient = null;
	private CoapServerGateway coapServer = null;
	private SystemPerformanceManager sysPerfMgr = null;
	private RedisPersistenceAdapter redisClient = null;
	
	// Humidity threshold tracking variables
	private ActuatorData   latestHumidifierActuatorData = null;
	private ActuatorData   latestHumidifierActuatorResponse = null;
	private SensorData     latestHumiditySensorData = null;
	private OffsetDateTime latestHumiditySensorTimeStamp = null;

	private boolean handleHumidityChangeOnDevice = false;
	private int     lastKnownHumidifierCommand   = ConfigConst.OFF_COMMAND;

	private long    humidityMaxTimePastThreshold = 300; // seconds
	private float   nominalHumiditySetting   = 40.0f;
	private float   triggerHumidifierFloor   = 30.0f;
	private float   triggerHumidifierCeiling = 50.0f;
	
	// constructors
	
	public DeviceDataManager()
	{
		super();
		
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		this.enableMqttClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);
		
		this.enableCoapServer =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);
		
		this.enableCoapClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_CLIENT_KEY);
		
		this.enableCloudClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_CLOUD_CLIENT_KEY);
		
		this.enablePersistenceClient =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_PERSISTENCE_CLIENT_KEY);
		
		// Parse config rules for local actuation events
		this.handleHumidityChangeOnDevice =
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, "handleHumidityChangeOnDevice");

		this.humidityMaxTimePastThreshold =
			configUtil.getInteger(
				ConfigConst.GATEWAY_DEVICE, "humidityMaxTimePastThreshold");

		this.nominalHumiditySetting =
			configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, "nominalHumiditySetting");

		this.triggerHumidifierFloor =
			configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, "triggerHumidifierFloor");

		this.triggerHumidifierCeiling =
			configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE, "triggerHumidifierCeiling");

		// Basic validation for timing
		if (this.humidityMaxTimePastThreshold < 10 || this.humidityMaxTimePastThreshold > 7200) {
			this.humidityMaxTimePastThreshold = 300;
		}
		
		initConnections();
	}
	
	public DeviceDataManager(
		boolean enableMqttClient,
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enableSmtpClient,
		boolean enablePersistenceClient)
	{
		super();
		
		initConnections();
	}
	
	
	// public methods - JedisPubSub callbacks
	
	/**
	 * Called when a message is received on a subscribed channel.
	 * 
	 * @param channel The channel name.
	 * @param message The message content (JSON).
	 */
	@Override
	public void onMessage(String channel, String message)
	{
		_Logger.info("Received Redis message on channel '" + channel + "': " + message);
		
		try {
			// Try to parse as SensorData (most common from CDA)
			if (channel.contains("SensorMsg")) {
				SensorData data = DataUtil.getInstance().jsonToSensorData(message);
				if (data != null) {
					this.handleSensorMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, data);
				}
			}
			// Try to parse as ActuatorData
			else if (channel.contains("ActuatorCmd") || channel.contains("ActuatorResponse")) {
				ActuatorData data = DataUtil.getInstance().jsonToActuatorData(message);
				if (data != null) {
					this.handleActuatorCommandResponse(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, data);
				}
			}
			// Try to parse as SystemPerformanceData
			else if (channel.contains("SystemPerfMsg")) {
				SystemPerformanceData data = DataUtil.getInstance().jsonToSystemPerformanceData(message);
				if (data != null) {
					this.handleSystemPerformanceMessage(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, data);
				}
			}
			// Generic message handling
			else {
				this.handleIncomingMessage(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, message);
			}
			
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to process Redis message from channel: " + channel, e);
		}
	}
	
	/**
	 * Called when subscription is successful.
	 * 
	 * @param channel The channel name.
	 * @param subscribedChannels The number of subscribed channels.
	 */
	@Override
	public void onSubscribe(String channel, int subscribedChannels)
	{
		_Logger.info("Successfully subscribed to Redis channel: " + channel + 
			" (Total subscriptions: " + subscribedChannels + ")");
	}
	
	/**
	 * Called when unsubscription is successful.
	 * 
	 * @param channel The channel name.
	 * @param subscribedChannels The remaining number of subscribed channels.
	 */
	@Override
	public void onUnsubscribe(String channel, int subscribedChannels)
	{
		_Logger.info("Unsubscribed from Redis channel: " + channel + 
			" (Remaining subscriptions: " + subscribedChannels + ")");
	}
	
	
	// public methods - IDataMessageListener implementation
	
	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data)
	{
		if (data != null) {
			_Logger.info("Handling actuator response: " + data.getName());
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for ActuatorData instance.");
			}
			
			// Store to Redis if persistence client is enabled
			if (this.enablePersistenceClient && this.redisClient != null) {
				String topic = resourceName.getResourceName();
				boolean success = this.redisClient.storeData(topic, 0, data);
				
				if (success) {
					_Logger.info("ActuatorData stored to Redis: " + data.getName());
				} else {
					_Logger.warning("Failed to store ActuatorData to Redis.");
				}
			}
			
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data)
	{
		if (data != null) {
			_Logger.log(
				Level.FINE,
				"Actuator request received: {0}. Message: {1}",
				new Object[] {resourceName.getResourceName(), Integer.valueOf((data.getCommand()))});
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for ActuatorData instance.");
			}
			
			int qos = ConfigConst.DEFAULT_QOS;
			
			// Send actuator command to CDA
			this.sendActuatorCommandtoCda(resourceName, data);
			
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		if (resourceName != null && msg != null) {
			try {
				if (resourceName == ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE) {
					_Logger.info("Handling incoming ActuatorData message: " + msg);
					
					// NOTE: it may seem wasteful to convert to ActuatorData and back while
					// the JSON data is already available; however, this provides a validation
					// scheme to ensure the data is actually an 'ActuatorData' instance
					// prior to sending off to the CDA
					ActuatorData ad = DataUtil.getInstance().jsonToActuatorData(msg);
					
					if (ad != null) {
						String jsonData = DataUtil.getInstance().actuatorDataToJson(ad);
						
						if (this.mqttClient != null) {
							_Logger.fine("Publishing ActuatorData to CDA via MQTT: " + jsonData);
							return this.mqttClient.publishMessage(resourceName, jsonData, ConfigConst.DEFAULT_QOS);
						}
						
						// TODO: If the GDA is hosting a CoAP server (or a CoAP client that
						// will connect to the CDA's CoAP server), you can add that logic here
						// in place of the MQTT client or in addition
					} else {
						_Logger.warning("Failed to parse ActuatorData from incoming message.");
						return false;
					}
				} else {
					_Logger.info("Handling incoming generic message: " + msg);
					return true;
				}
			} catch (Exception e) {
				_Logger.log(Level.WARNING, "Failed to process incoming message for resource: " + resourceName, e);
			}
		} else {
			_Logger.warning("Incoming message has no data. Ignoring for resource: " + resourceName);
		}
		
		return false;
	}

	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		if (data != null) {
			_Logger.fine("Handling sensor message: " + data.getName());
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for SensorData instance.");
			}
			
			String jsonData = DataUtil.getInstance().sensorDataToJson(data);
			
			_Logger.fine("JSON [SensorData] -> " + jsonData);
			
			int qos = ConfigConst.DEFAULT_QOS;
			
			if (this.enablePersistenceClient && this.redisClient != null) {
				this.redisClient.storeData(resourceName.getResourceName(), qos, data);
			}
			
			this.handleIncomingDataAnalysis(resourceName, data);
			
			this.handleUpstreamTransmission(resourceName, data, qos);
			
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		if (data != null) {
			_Logger.info("Handling system performance message: " + data.getName());
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for SystemPerformanceData instance.");
			}
			
			int qos = ConfigConst.DEFAULT_QOS;
			
			// Store to Redis if persistence client is enabled
			if (this.enablePersistenceClient && this.redisClient != null) {
				String topic = resourceName.getResourceName();
				boolean success = this.redisClient.storeData(topic, 0, data);
				
				if (success) {
					_Logger.info("SystemPerformanceData stored to Redis: " + data.getName());
				} else {
					_Logger.warning("Failed to store SystemPerformanceData to Redis.");
				}
			}
			
			this.handleUpstreamTransmission(resourceName, data, qos);
			
			return true;
		} else {
			return false;
		}
	}
	
	public void setActuatorDataListener(String name, IActuatorDataListener listener)
	{
		if (listener != null) {
			// For now, just ignore 'name' - if you need more than one listener,
			// you can use 'name' to create a map of listener instances
			this.actuatorDataListener = listener;
		}
	}
	
	public void startManager()
	{
		_Logger.info("Starting DeviceDataManager...");
		
		if (this.cloudClient != null) {
			if (this.cloudClient.connectClient()) {
				_Logger.info("Successfully connected cloud client to cloud service.");
			} else {
				_Logger.severe("Failed to connect cloud client to cloud service.");
			}
		}
		
		if (this.sysPerfMgr != null) {
			this.sysPerfMgr.startManager();
		}
		
		if (this.redisClient != null) {
			if (this.redisClient.connectClient()) {
				_Logger.info("Redis persistence client connected successfully.");
				
				// Subscribe to CDA sensor messages
				this.redisClient.subscribeToChannel(this, ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
				_Logger.info("Subscribed to CDA sensor messages via Redis.");
				
			} else {
				_Logger.warning("Failed to connect Redis persistence client.");
			}
		}
		
		if (this.mqttClient != null) {
			if (this.mqttClient.connectClient()) {
				_Logger.info("Successfully connected MQTT client to broker.");
				
				// IMPORTANT NOTE: The 'subscribeToTopic()' method calls shown
				// below are now moved to MqttClientConnector.connectComplete()
				// in Lab Module 10.
				//int qos = ConfigConst.DEFAULT_QOS;
				
				//this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos);
				//this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos);
				//this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos);
				//this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos);
			} else {
				_Logger.severe("Failed to connect MQTT client to broker.");
			}
		}
		
		if (this.coapServer != null) {
			if (this.coapServer.startServer()) {
				_Logger.info("CoAP server started.");
			} else {
				_Logger.severe("Failed to start CoAP server. Check log file for details.");
			}
		}
	}
	
	public void stopManager()
	{
		_Logger.info("Stopping DeviceDataManager...");
		
		if (this.sysPerfMgr != null) {
			this.sysPerfMgr.stopManager();
		}
		
		// Unsubscribe from Redis channels
		if (this.isSubscribed()) {
			this.unsubscribe();
			_Logger.info("Unsubscribed from Redis channels.");
		}
		
		if (this.redisClient != null) {
			if (this.redisClient.disconnectClient()) {
				_Logger.info("Redis persistence client disconnected successfully.");
			} else {
				_Logger.warning("Failed to disconnect Redis persistence client.");
			}
		}
		
		if (this.mqttClient != null) {
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE);
			
			if (this.mqttClient.disconnectClient()) {
				_Logger.info("Successfully disconnected MQTT client from broker.");
			} else {
				_Logger.severe("Failed to disconnect MQTT client from broker.");
			}
		}
		
		if (this.coapServer != null) {
			if (this.coapServer.stopServer()) {
				_Logger.info("CoAP server stopped.");
			} else {
				_Logger.severe("Failed to stop CoAP server. Check log file for details.");
			}
		}
		
		if (this.cloudClient != null) {
			if (this.cloudClient.disconnectClient()) {
				_Logger.info("Successfully disconnected cloud client from cloud service.");
			} else {
				_Logger.severe("Failed to disconnect cloud client from cloud service.");
			}
		}
	}

	
	// private methods
	
	/**
	 * Initializes the enabled connections. This will NOT start them, but only create the
	 * instances that will be used in the {@link #startManager() and #stopManager()) methods.
	 * 
	 */
	private void initConnections()
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		this.enableSystemPerf =
			configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_SYSTEM_PERF_KEY);
		
		if (this.enableSystemPerf) {
			this.sysPerfMgr = new SystemPerformanceManager();
			this.sysPerfMgr.setDataMessageListener(this);
		}
		
		if (this.enableMqttClient) {
			this.mqttClient = new MqttClientConnector();
			this.mqttClient.setDataMessageListener(this);
		}
		
		if (this.enableCoapClient) {
			this.coapClient = new CoapClientConnector();
			this.coapClient.setDataMessageListener(this);
			_Logger.info("CoAP client connector created.");
		}
		
		if (this.enableCoapServer) {
			this.coapServer = new CoapServerGateway(this);
			_Logger.info("CoAP server gateway created.");
		}
		
		if (this.enableCloudClient) {
			this.cloudClient = new CloudClientConnector();
			
			// Set the data message listener for cloud client
			if (this.cloudClient != null) {
				this.cloudClient.setDataMessageListener(this);
			}
			
			_Logger.info("Cloud client connector created.");
		}
		
		if (this.enablePersistenceClient) {
			this.redisClient = new RedisPersistenceAdapter();
			_Logger.info("Redis persistence client created.");
		}
	}
	
	/**
	 * Handles incoming data analysis for ActuatorData.
	 * 
	 * @param resourceName The resource name.
	 * @param data The ActuatorData instance.
	 */
	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, ActuatorData data)
	{
		_Logger.fine("Analyzing incoming actuator data: " + data.getName());
		
		if (data.isResponseFlagEnabled()) {
			// TODO: implement response handling
		} else {
			if (this.actuatorDataListener != null) {
				this.actuatorDataListener.onActuatorDataUpdate(data);
			}
		}
	}
	
	/**
	 * Handles incoming data analysis for SensorData.
	 * 
	 * @param resourceName The resource name.
	 * @param data The SensorData instance.
	 */
	private void handleIncomingDataAnalysis(ResourceNameEnum resource, SensorData data)
	{
		// check either resource or SensorData for type
		if (data.getTypeID() == ConfigConst.HUMIDITY_SENSOR_TYPE) {
			handleHumiditySensorAnalysis(resource, data);
		}
	}
	
	/**
	 * Handles incoming data analysis for SystemStateData.
	 * 
	 * @param resourceName The resource name.
	 * @param data The SystemStateData instance.
	 */
	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, SystemStateData data)
	{
		_Logger.fine("Analyzing incoming system state data: " + data.getName());
		
		// TODO: Add analysis logic in future exercises
	}
	
	/**
	 * Analyzes humidity sensor data and triggers actuation events.
	 * 
	 * @param resource The resource name.
	 * @param data The SensorData instance.
	 */
	private void handleHumiditySensorAnalysis(ResourceNameEnum resource, SensorData data)
	{
		_Logger.fine("Analyzing humidity data from CDA: " + data.getLocationID() + ". Value: " + data.getValue());
		
		boolean isLow  = data.getValue() < this.triggerHumidifierFloor;
		boolean isHigh = data.getValue() > this.triggerHumidifierCeiling;
		
		if (isLow || isHigh) {
			_Logger.fine("Humidity data from CDA exceeds nominal range.");
			
			if (this.latestHumiditySensorData == null) {
				// set properties then exit - nothing more to do until the next sample
				this.latestHumiditySensorData = data;
				this.latestHumiditySensorTimeStamp = getDateTimeFromData(data);
				
				_Logger.fine(
					"Starting humidity nominal exception timer. Waiting for seconds: " +
					this.humidityMaxTimePastThreshold);
				
				return;
			} else {
				OffsetDateTime curHumiditySensorTimeStamp = getDateTimeFromData(data);
				
				long diffSeconds =
					ChronoUnit.SECONDS.between(
						this.latestHumiditySensorTimeStamp, curHumiditySensorTimeStamp);
				
				_Logger.fine("Checking Humidity value exception time delta: " + diffSeconds);
				
				if (diffSeconds >= this.humidityMaxTimePastThreshold) {
					ActuatorData ad = new ActuatorData();
					ad.setName(ConfigConst.HUMIDIFIER_ACTUATOR_NAME);
					ad.setLocationID(data.getLocationID());
					ad.setTypeID(ConfigConst.HUMIDIFIER_ACTUATOR_TYPE);
					ad.setValue(this.nominalHumiditySetting);
					
					if (isLow) {
						ad.setCommand(ConfigConst.ON_COMMAND);
					} else if (isHigh) {
						ad.setCommand(ConfigConst.OFF_COMMAND);
					}
					
					_Logger.info(
						"Humidity exceptional value reached. Sending actuation event to CDA: " +
						ad);
					
					this.lastKnownHumidifierCommand = ad.getCommand();
					sendActuatorCommandtoCda(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, ad);
					
					// set ActuatorData and reset SensorData (and timestamp)
					this.latestHumidifierActuatorData = ad;
					this.latestHumiditySensorData = null;
					this.latestHumiditySensorTimeStamp = null;
				}
			}
		} else if (this.lastKnownHumidifierCommand == ConfigConst.ON_COMMAND) {
			// check if we need to turn off the humidifier
			if (this.latestHumidifierActuatorData != null) {
				// check the value - if the humidifier is on, but not yet at nominal, keep it on
				if (this.latestHumidifierActuatorData.getValue() >= this.nominalHumiditySetting) {
					this.latestHumidifierActuatorData.setCommand(ConfigConst.OFF_COMMAND);
					
					_Logger.info(
						"Humidity nominal value reached. Sending OFF actuation event to CDA: " +
						this.latestHumidifierActuatorData);
					
					sendActuatorCommandtoCda(
						ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, this.latestHumidifierActuatorData);
					
					// reset ActuatorData and SensorData (and timestamp)
					this.lastKnownHumidifierCommand = this.latestHumidifierActuatorData.getCommand();
					this.latestHumidifierActuatorData = null;
					this.latestHumiditySensorData = null;
					this.latestHumiditySensorTimeStamp = null;
				} else {
					_Logger.fine("Humidifier is still on. Not yet at nominal levels (OK).");
				}
			} else {
				_Logger.warning(
					"ERROR: ActuatorData for humidifier is null (shouldn't be). Can't send command.");
			}
		}
	}
	
	/**
	 * Sends actuation command to CDA via MQTT or CoAP.
	 * 
	 * @param resource The resource name.
	 * @param data The ActuatorData command.
	 */
	private void sendActuatorCommandtoCda(ResourceNameEnum resource, ActuatorData data)
	{
		// NOTE: This is how an ActuatorData command will get passed to the CDA
		// when the GDA is providing the CoAP server and hosting the appropriate
		// ActuatorData resource.
		if (this.actuatorDataListener != null) {
			this.actuatorDataListener.onActuatorDataUpdate(data);
		}
		
		// NOTE: This is how an ActuatorData command will get passed to the CDA
		// when using MQTT to communicate between the GDA and CDA
		if (this.enableMqttClient && this.mqttClient != null) {
			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);
			
			if (this.mqttClient.publishMessage(resource, jsonData, ConfigConst.DEFAULT_QOS)) {
				_Logger.info(
					"Published ActuatorData command from GDA to CDA: " + data.getCommand());
			} else {
				_Logger.warning(
					"Failed to publish ActuatorData command from GDA to CDA: " + data.getCommand());
			}
		}
	}
	
	/**
	 * Extracts OffsetDateTime from BaseIotData timestamp.
	 * 
	 * @param data The BaseIotData instance.
	 * @return OffsetDateTime The parsed timestamp.
	 */
	private OffsetDateTime getDateTimeFromData(BaseIotData data)
	{
		OffsetDateTime odt = null;
		
		try {
			odt = OffsetDateTime.parse(data.getTimeStamp());
		} catch (Exception e) {
			_Logger.warning(
				"Failed to extract ISO 8601 timestamp from IoT data. Using local current time.");
			
			odt = OffsetDateTime.now();
		}
		
		return odt;
	}
	
	/**
	 * Handles upstream transmission of data to cloud services.
	 * Overloaded method for SensorData.
	 * 
	 * @param resource The resource name.
	 * @param data The SensorData to transmit.
	 * @param qos The quality of service level.
	 * @return boolean True if successful, false otherwise.
	 */
	private boolean handleUpstreamTransmission(ResourceNameEnum resource, SensorData data, int qos)
	{
		_Logger.fine("Sending SensorData to cloud service: " + resource);
		
		if (this.cloudClient != null) {
			if (this.cloudClient.sendEdgeDataToCloud(resource, data)) {
				_Logger.fine("Sent SensorData to cloud service.");
				return true;
			} else {
				_Logger.warning("Failed to send SensorData to cloud service.");
			}
		}
		
		return false;
	}
	
	/**
	 * Handles upstream transmission of data to cloud services.
	 * Overloaded method for SystemPerformanceData.
	 * 
	 * @param resource The resource name.
	 * @param data The SystemPerformanceData to transmit.
	 * @param qos The quality of service level.
	 * @return boolean True if successful, false otherwise.
	 */
	private boolean handleUpstreamTransmission(ResourceNameEnum resource, SystemPerformanceData data, int qos)
	{
		_Logger.fine("Sending SystemPerformanceData to cloud service: " + resource);
		
		if (this.cloudClient != null) {
			if (this.cloudClient.sendEdgeDataToCloud(resource, data)) {
				_Logger.fine("Sent SystemPerformanceData to cloud service.");
				return true;
			} else {
				_Logger.warning("Failed to send SystemPerformanceData to cloud service.");
			}
		}
		
		return false;
	}
	
}