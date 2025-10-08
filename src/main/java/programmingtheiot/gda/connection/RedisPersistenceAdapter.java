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

package programmingtheiot.gda.connection;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Redis-based persistence adapter for storing and retrieving IoT data.
 * 
 */
public class RedisPersistenceAdapter implements IPersistenceClient
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(RedisPersistenceAdapter.class.getName());
	
	// private var's
	
	private String host = ConfigConst.DEFAULT_HOST;
	private int port = 6379;
	private Jedis redisClient = null;
	private Jedis redisSubClient = null;
	private boolean isConnected = false;
	
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public RedisPersistenceAdapter()
	{
		super();
		
		initConfig();
	}
	
	
	// public methods
	
	/**
	 * Connects to the Redis server.
	 * 
	 * @return boolean True if connected successfully, false otherwise.
	 */
	@Override
	public boolean connectClient()
	{
		if (this.isConnected) {
			_Logger.warning("Redis client is already connected.");
			return true;
		}
		
		try {
			this.redisClient = new Jedis(this.host, this.port);
			this.redisClient.connect();
			
			// Test the connection
			String response = this.redisClient.ping();
			
			if (response != null && response.equalsIgnoreCase("PONG")) {
				this.isConnected = true;
				_Logger.info("Successfully connected to Redis server at " + this.host + ":" + this.port);
				return true;
			} else {
				_Logger.warning("Failed to connect to Redis server - no PONG response.");
				return false;
			}
		} catch (JedisConnectionException e) {
			_Logger.log(Level.SEVERE, "Failed to connect to Redis server at " + this.host + ":" + this.port, e);
			return false;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error connecting to Redis server.", e);
			return false;
		}
	}

	/**
	 * Disconnects from the Redis server.
	 * 
	 * @return boolean True if disconnected successfully, false otherwise.
	 */
	@Override
	public boolean disconnectClient()
	{
		if (!this.isConnected) {
			_Logger.warning("Redis client is already disconnected.");
			return true;
		}
		
		try {
			if (this.redisClient != null) {
				this.redisClient.close();
				this.isConnected = false;
			}
			
			if (this.redisSubClient != null) {
				this.redisSubClient.close();
			}
			
			_Logger.info("Successfully disconnected from Redis server.");
			return true;
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error disconnecting from Redis server.", e);
			return false;
		}
	}

	/**
	 * Retrieves ActuatorData from Redis based on topic and date range.
	 * 
	 * @param topic The topic/key to retrieve data from.
	 * @param startDate The start date for filtering (can be null).
	 * @param endDate The end date for filtering (can be null).
	 * @return ActuatorData[] Array of ActuatorData instances.
	 */
	@Override
	public ActuatorData[] getActuatorData(String topic, Date startDate, Date endDate)
	{
		if (!this.isConnected || this.redisClient == null) {
			_Logger.warning("Redis client is not connected. Cannot retrieve ActuatorData.");
			return null;
		}
		
		try {
			// Get all keys matching the topic pattern
			Set<String> keys = this.redisClient.keys(topic + ":*");
			List<ActuatorData> dataList = new ArrayList<>();
			
			for (String key : keys) {
				String jsonData = this.redisClient.get(key);
				
				if (jsonData != null && !jsonData.isEmpty()) {
					ActuatorData data = DataUtil.getInstance().jsonToActuatorData(jsonData);
					
					if (data != null) {
						// Filter by date if provided
						if (startDate != null || endDate != null) {
							long dataTime = data.getTimeStampMillis();
							
							if (startDate != null && dataTime < startDate.getTime()) {
								continue;
							}
							
							if (endDate != null && dataTime > endDate.getTime()) {
								continue;
							}
						}
						
						dataList.add(data);
					}
				}
			}
			
			_Logger.info("Retrieved " + dataList.size() + " ActuatorData entries for topic: " + topic);
			return dataList.toArray(new ActuatorData[0]);
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error retrieving ActuatorData from Redis.", e);
			return null;
		}
	}

	/**
	 * Retrieves SensorData from Redis based on topic and date range.
	 * 
	 * @param topic The topic/key to retrieve data from.
	 * @param startDate The start date for filtering (can be null).
	 * @param endDate The end date for filtering (can be null).
	 * @return SensorData[] Array of SensorData instances.
	 */
	@Override
	public SensorData[] getSensorData(String topic, Date startDate, Date endDate)
	{
		if (!this.isConnected || this.redisClient == null) {
			_Logger.warning("Redis client is not connected. Cannot retrieve SensorData.");
			return null;
		}
		
		try {
			// Get all keys matching the topic pattern
			Set<String> keys = this.redisClient.keys(topic + ":*");
			List<SensorData> dataList = new ArrayList<>();
			
			for (String key : keys) {
				String jsonData = this.redisClient.get(key);
				
				if (jsonData != null && !jsonData.isEmpty()) {
					SensorData data = DataUtil.getInstance().jsonToSensorData(jsonData);
					
					if (data != null) {
						// Filter by date if provided
						if (startDate != null || endDate != null) {
							long dataTime = data.getTimeStampMillis();
							
							if (startDate != null && dataTime < startDate.getTime()) {
								continue;
							}
							
							if (endDate != null && dataTime > endDate.getTime()) {
								continue;
							}
						}
						
						dataList.add(data);
					}
				}
			}
			
			_Logger.info("Retrieved " + dataList.size() + " SensorData entries for topic: " + topic);
			return dataList.toArray(new SensorData[0]);
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error retrieving SensorData from Redis.", e);
			return null;
		}
	}

	/**
	 * Registers a data storage listener (not implemented).
	 * 
	 * @param cType The class type.
	 * @param listener The listener instance.
	 * @param topics The topics to listen to.
	 */
	@Override
	public void registerDataStorageListener(Class cType, IPersistenceListener listener, String... topics)
	{
		// Optional - not implemented for this exercise
		_Logger.info("registerDataStorageListener not implemented.");
	}

	/**
	 * Stores ActuatorData to Redis.
	 * 
	 * @param topic The topic/key to store data under.
	 * @param qos Quality of service (not used for Redis).
	 * @param data Array of ActuatorData to store.
	 * @return boolean True if stored successfully, false otherwise.
	 */
	@Override
	public boolean storeData(String topic, int qos, ActuatorData... data)
	{
		if (!this.isConnected || this.redisClient == null) {
			_Logger.warning("Redis client is not connected. Cannot store ActuatorData.");
			return false;
		}
		
		if (data == null || data.length == 0) {
			_Logger.warning("No ActuatorData to store.");
			return false;
		}
		
		try {
			for (ActuatorData actuatorData : data) {
				if (actuatorData != null) {
					String jsonData = DataUtil.getInstance().actuatorDataToJson(actuatorData);
					
					if (jsonData != null) {
						String key = topic + ":" + actuatorData.getTimeStampMillis();
						this.redisClient.set(key, jsonData);
						
						// Publish to channel for subscribers
						this.redisClient.publish(topic, jsonData);
					}
				}
			}
			
			_Logger.info("Stored " + data.length + " ActuatorData entries to Redis under topic: " + topic);
			return true;
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error storing ActuatorData to Redis.", e);
			return false;
		}
	}

	/**
	 * Stores SensorData to Redis.
	 * 
	 * @param topic The topic/key to store data under.
	 * @param qos Quality of service (not used for Redis).
	 * @param data Array of SensorData to store.
	 * @return boolean True if stored successfully, false otherwise.
	 */
	@Override
	public boolean storeData(String topic, int qos, SensorData... data)
	{
		if (!this.isConnected || this.redisClient == null) {
			_Logger.warning("Redis client is not connected. Cannot store SensorData.");
			return false;
		}
		
		if (data == null || data.length == 0) {
			_Logger.warning("No SensorData to store.");
			return false;
		}
		
		try {
			for (SensorData sensorData : data) {
				if (sensorData != null) {
					String jsonData = DataUtil.getInstance().sensorDataToJson(sensorData);
					
					if (jsonData != null) {
						String key = topic + ":" + sensorData.getTimeStampMillis();
						this.redisClient.set(key, jsonData);
						
						// Publish to channel for subscribers
						this.redisClient.publish(topic, jsonData);
					}
				}
			}
			
			_Logger.info("Stored " + data.length + " SensorData entries to Redis under topic: " + topic);
			return true;
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error storing SensorData to Redis.", e);
			return false;
		}
	}

	/**
	 * Stores SystemPerformanceData to Redis.
	 * 
	 * @param topic The topic/key to store data under.
	 * @param qos Quality of service (not used for Redis).
	 * @param data Array of SystemPerformanceData to store.
	 * @return boolean True if stored successfully, false otherwise.
	 */
	@Override
	public boolean storeData(String topic, int qos, SystemPerformanceData... data)
	{
		if (!this.isConnected || this.redisClient == null) {
			_Logger.warning("Redis client is not connected. Cannot store SystemPerformanceData.");
			return false;
		}
		
		if (data == null || data.length == 0) {
			_Logger.warning("No SystemPerformanceData to store.");
			return false;
		}
		
		try {
			for (SystemPerformanceData sysPerfData : data) {
				if (sysPerfData != null) {
					String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(sysPerfData);
					
					if (jsonData != null) {
						String key = topic + ":" + sysPerfData.getTimeStampMillis();
						this.redisClient.set(key, jsonData);
						
						// Publish to channel for subscribers
						this.redisClient.publish(topic, jsonData);
					}
				}
			}
			
			_Logger.info("Stored " + data.length + " SystemPerformanceData entries to Redis under topic: " + topic);
			return true;
			
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error storing SystemPerformanceData to Redis.", e);
			return false;
		}
	}
	
	/**
	 * Subscribes to a Redis channel using JedisPubSub.
	 * This method runs in a separate thread since subscribe() is blocking.
	 * 
	 * @param subscriber The JedisPubSub instance to handle callbacks.
	 * @param resource The ResourceNameEnum for the channel to subscribe to.
	 */
	public void subscribeToChannel(final JedisPubSub subscriber, final ResourceNameEnum resource)
	{
		if (!this.isConnected) {
			_Logger.warning("Redis client is not connected. Cannot subscribe to channel.");
			return;
		}
		
		// Create a new thread for subscription since subscribe() is blocking
		Thread subThread = new Thread(new Runnable() {
			@Override
			public void run()
			{
				try {
					// Create a separate Jedis client for subscription
					redisSubClient = new Jedis(host, port);
					
					String channel = resource.getResourceName();
					_Logger.info("Subscribing to Redis channel: " + channel);
					
					// This call blocks until unsubscribed
					redisSubClient.subscribe(subscriber, channel);
					
				} catch (Exception e) {
					_Logger.log(Level.SEVERE, "Error in Redis subscription thread.", e);
				}
			}
		});
		
		subThread.setDaemon(true);
		subThread.start();
		
		_Logger.info("Redis subscription thread started for channel: " + resource.getResourceName());
	}
	
	
	// private methods
	
	/**
	 * Initializes configuration from the config file.
	 */
	private void initConfig()
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		this.host = 
			configUtil.getProperty(
				ConfigConst.DATA_GATEWAY_SERVICE, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);
		
		this.port = 
			configUtil.getInteger(
				ConfigConst.DATA_GATEWAY_SERVICE, ConfigConst.PORT_KEY, 6379);
		
		_Logger.info("Redis Persistence Adapter configured for host: " + this.host + ", port: " + this.port);
	}

}