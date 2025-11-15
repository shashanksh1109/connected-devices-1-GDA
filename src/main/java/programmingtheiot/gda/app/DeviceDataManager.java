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

import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.data.SystemStateData;
import programmingtheiot.gda.connection.CoapClientConnector;
import programmingtheiot.gda.connection.CoapServerGateway;
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
	private IPubSubClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private IRequestResponseClient coapClient = null;
	private CoapServerGateway coapServer = null;
	private SystemPerformanceManager sysPerfMgr = null;
	private RedisPersistenceAdapter redisClient = null;
	
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
			_Logger.info("Handling actuator command request: " + data.getName());
			
			// Forward to actuator data listener if available
			if (this.actuatorDataListener != null) {
				this.actuatorDataListener.onActuatorDataUpdate(data);
			}
			
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		if (msg != null) {
			_Logger.info("Handling incoming generic message: " + msg);
			
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		if (data != null) {
			_Logger.info("Handling sensor message from CDA: " + data.getName() + 
				", Value: " + data.getValue());
			
			if (data.hasError()) {
				_Logger.warning("Error flag set for SensorData instance.");
			}
			
			// Store to Redis if persistence client is enabled
			if (this.enablePersistenceClient && this.redisClient != null) {
				String topic = resourceName.getResourceName();
				boolean success = this.redisClient.storeData(topic, 0, data);
				
				if (success) {
					_Logger.info("SensorData stored to Redis: " + data.getName());
				} else {
					_Logger.warning("Failed to store SensorData to Redis.");
				}
			}
			
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
				
				int qos = ConfigConst.DEFAULT_QOS;
				
				this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos);
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
		
		if (this.cloudClient != null) {
			// TODO: implement this in Lab Module 10
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
			// TODO: implement this in Lab Module 10
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
			// TODO: implement this in Lab Module 10
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
		_Logger.info("Analyzing incoming actuator data: " + data.getName());
		
		if (data.isResponseFlagEnabled()) {
			// TODO: implement response handling
		} else {
			if (this.actuatorDataListener != null) {
				this.actuatorDataListener.onActuatorDataUpdate(data);
			}
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
	 * Handles upstream transmission of data to cloud services.
	 * 
	 * @param resourceName The resource name.
	 * @param jsonData The JSON data to transmit.
	 * @param qos The quality of service level.
	 * @return boolean True if successful, false otherwise.
	 */
	private boolean handleUpstreamTransmission(ResourceNameEnum resourceName, String jsonData, int qos)
	{
		_Logger.fine("Handling upstream transmission for resource: " + resourceName);
		
		// TODO: Implement cloud transmission in Part 03
		
		return true;
	}
	
}