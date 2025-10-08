/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 */

package programmingtheiot.integration.connection;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;

/**
 * Integration test for RedisPersistenceAdapter.
 * 
 * NOTE: Redis server must be running on localhost:6379 for these tests to pass.
 */
public class RedisClientAdapterTest
{
	private static final Logger _Logger =
		Logger.getLogger(RedisClientAdapterTest.class.getName());
	
	private RedisPersistenceAdapter rpa = null;
	
	private static final String ACTUATOR_TEST_TOPIC = "PIOT/GatewayDevice/ActuatorCmd";
	private static final String SENSOR_TEST_TOPIC = "PIOT/GatewayDevice/SensorMsg";
	private static final String SYSPERF_TEST_TOPIC = "PIOT/GatewayDevice/SystemPerfMsg";
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		_Logger.info("Setting up RedisClientAdapterTest...");
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		_Logger.info("Tearing down RedisClientAdapterTest...");
	}
	
	@Before
	public void setUp() throws Exception
	{
		this.rpa = new RedisPersistenceAdapter();
	}
	
	@After
	public void tearDown() throws Exception
	{
		if (this.rpa != null) {
			this.rpa.disconnectClient();
		}
	}
	
	@Test
	public void testConnectClient()
	{
		_Logger.info("Testing Redis client connection...");
		
		assertTrue("Failed to connect to Redis", this.rpa.connectClient());
		
		_Logger.info("Redis client connected successfully.");
	}
	
	@Test
	public void testDisconnectClient()
	{
		_Logger.info("Testing Redis client disconnection...");
		
		// Connect first
		assertTrue("Failed to connect to Redis", this.rpa.connectClient());
		
		// Then disconnect
		assertTrue("Failed to disconnect from Redis", this.rpa.disconnectClient());
		
		_Logger.info("Redis client disconnected successfully.");
	}
	
	@Test
	public void testStoreDataStringIntActuatorDataArray()
	{
		_Logger.info("Testing ActuatorData storage...");
		
		// Connect to Redis
		assertTrue("Failed to connect to Redis", this.rpa.connectClient());
		
		// Create test ActuatorData
		ActuatorData ad1 = new ActuatorData();
		ad1.setName("TestActuator1");
		ad1.setCommand(1);
		ad1.setValue(22.5f);
		
		ActuatorData ad2 = new ActuatorData();
		ad2.setName("TestActuator2");
		ad2.setCommand(0);
		ad2.setValue(18.0f);
		
		// Store data
		assertTrue("Failed to store ActuatorData", 
			this.rpa.storeData(ACTUATOR_TEST_TOPIC, 0, ad1, ad2));
		
		_Logger.info("ActuatorData stored successfully.");
	}
	
	@Test
	public void testGetActuatorData()
	{
		_Logger.info("Testing ActuatorData retrieval...");
		
		// Connect to Redis
		assertTrue("Failed to connect to Redis", this.rpa.connectClient());
		
		// Create and store test ActuatorData
		ActuatorData ad = new ActuatorData();
		ad.setName("TestActuatorRetrieve");
		ad.setCommand(1);
		ad.setValue(25.0f);
		
		assertTrue("Failed to store ActuatorData", 
			this.rpa.storeData(ACTUATOR_TEST_TOPIC, 0, ad));
		
		// Retrieve data
		ActuatorData[] dataArray = this.rpa.getActuatorData(ACTUATOR_TEST_TOPIC, null, null);
		
		assertNotNull("Retrieved ActuatorData array is null", dataArray);
		assertTrue("No ActuatorData retrieved", dataArray.length > 0);
		
		_Logger.info("Retrieved " + dataArray.length + " ActuatorData entries.");
	}
	
	@Test
	public void testStoreDataStringIntSensorDataArray()
	{
		_Logger.info("Testing SensorData storage...");
		
		// Connect to Redis
		assertTrue("Failed to connect to Redis", this.rpa.connectClient());
		
		// Create test SensorData
		SensorData sd1 = new SensorData();
		sd1.setName("TestSensor1");
		sd1.setValue(23.5f);
		
		SensorData sd2 = new SensorData();
		sd2.setName("TestSensor2");
		sd2.setValue(45.2f);
		
		// Store data
		assertTrue("Failed to store SensorData", 
			this.rpa.storeData(SENSOR_TEST_TOPIC, 0, sd1, sd2));
		
		_Logger.info("SensorData stored successfully.");
	}
	
	@Test
	public void testGetSensorData()
	{
		_Logger.info("Testing SensorData retrieval...");
		
		// Connect to Redis
		assertTrue("Failed to connect to Redis", this.rpa.connectClient());
		
		// Create and store test SensorData
		SensorData sd = new SensorData();
		sd.setName("TestSensorRetrieve");
		sd.setValue(30.5f);
		
		assertTrue("Failed to store SensorData", 
			this.rpa.storeData(SENSOR_TEST_TOPIC, 0, sd));
		
		// Retrieve data
		SensorData[] dataArray = this.rpa.getSensorData(SENSOR_TEST_TOPIC, null, null);
		
		assertNotNull("Retrieved SensorData array is null", dataArray);
		assertTrue("No SensorData retrieved", dataArray.length > 0);
		
		_Logger.info("Retrieved " + dataArray.length + " SensorData entries.");
	}
	
	@Test
	public void testStoreDataStringIntSystemPerformanceDataArray()
	{
		_Logger.info("Testing SystemPerformanceData storage...");
		
		// Connect to Redis
		assertTrue("Failed to connect to Redis", this.rpa.connectClient());
		
		// Create test SystemPerformanceData
		SystemPerformanceData spd1 = new SystemPerformanceData();
		spd1.setCpuUtilization(45.5f);
		spd1.setMemoryUtilization(62.3f);
		spd1.setDiskUtilization(38.7f);
		
		SystemPerformanceData spd2 = new SystemPerformanceData();
		spd2.setCpuUtilization(50.2f);
		spd2.setMemoryUtilization(58.9f);
		spd2.setDiskUtilization(40.1f);
		
		// Store data
		assertTrue("Failed to store SystemPerformanceData", 
			this.rpa.storeData(SYSPERF_TEST_TOPIC, 0, spd1, spd2));
		
		_Logger.info("SystemPerformanceData stored successfully.");
	}
}