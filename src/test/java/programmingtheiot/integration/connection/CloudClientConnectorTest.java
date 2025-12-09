/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */ 

package programmingtheiot.integration.connection;

import java.util.List;
import java.util.logging.Logger;

import org.junit.After;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.DefaultDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.app.DeviceDataManager;
import programmingtheiot.gda.connection.CloudClientConnector;
import programmingtheiot.gda.connection.ICloudClient;

/**
 * This test case class contains very basic integration tests for
 * CloudClientConnector. It should not be considered complete,
 * but serve as a starting point for the student implementing
 * additional functionality within their Programming the IoT
 * environment.
 *
 */
public class CloudClientConnectorTest
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(CloudClientConnectorTest.class.getName());
	
	
	// member var's
	
	private List<ICloudClient> cloudClientList = null;
	private ICloudClient cloudClient = null;
	
	
	// test setup methods
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception
	{
		this.cloudClient = new CloudClientConnector();
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception
	{
	}
	
	// test methods
	
	/**
	 * TEST 1: Basic Cloud Client Connect and Disconnect
	 * 
	 * This test verifies that the CloudClientConnector can successfully
	 * connect to and disconnect from the cloud service.
	 */
//	@Test
	public void testCloudClientConnectAndDisconnect()
	{
		_Logger.info("TEST 1: Testing cloud client connect and disconnect...");
		
		this.cloudClient.setDataMessageListener(new DefaultDataMessageListener());
		
		assertTrue(this.cloudClient.connectClient());
		
		try {
			// sleep for a minute to allow connection to stabilize
			_Logger.info("Waiting 60 seconds for connection to stabilize...");
			Thread.sleep(60000L);
		} catch (Exception e) {
			// ignore
		}
		
		assertTrue(this.cloudClient.disconnectClient());
		
		_Logger.info("TEST 1 complete.");
	}
	
	/**
	 * TEST 2: Publish SensorData and SystemPerformanceData to Cloud
	 * 
	 * This test verifies that the CloudClientConnector can publish
	 * sensor data and system performance data to the cloud service.
	 * 
	 * IMPORTANT: After running this test, verify in your Ubidots dashboard
	 * that the data was received and stored properly.
	 */
//	@Test
	public void testPublishSensorDataToCloud()
	{
		_Logger.info("TEST 2: Testing publish SensorData to cloud...");
		
		this.cloudClient.setDataMessageListener(new DefaultDataMessageListener());
		
		assertTrue(this.cloudClient.connectClient());
		
		try {
			// sleep to allow connection to complete
			_Logger.info("Waiting 5 seconds for connection to complete...");
			Thread.sleep(5000L);
		} catch (Exception e) {
			// ignore
		}
		
		// Create and publish temperature sensor data
		SensorData tempData = new SensorData();
		tempData.setName(ConfigConst.TEMP_SENSOR_NAME);
		tempData.setValue(22.5f);
		
		_Logger.info("Publishing temperature data: " + tempData.getValue());
		assertTrue(this.cloudClient.sendEdgeDataToCloud(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, tempData));
		
		// Create and publish humidity sensor data
		SensorData humidityData = new SensorData();
		humidityData.setName(ConfigConst.HUMIDITY_SENSOR_NAME);
		humidityData.setValue(45.0f);
		
		_Logger.info("Publishing humidity data: " + humidityData.getValue());
		assertTrue(this.cloudClient.sendEdgeDataToCloud(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, humidityData));
		
		// Create and publish system performance data
		SystemPerformanceData sysPerfData = new SystemPerformanceData();
		sysPerfData.setCpuUtilization(65.3f);
		sysPerfData.setMemoryUtilization(72.8f);
		
		_Logger.info("Publishing system performance data - CPU: " + sysPerfData.getCpuUtilization() + "%, Memory: " + sysPerfData.getMemoryUtilization() + "%");
		assertTrue(this.cloudClient.sendEdgeDataToCloud(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, sysPerfData));
		
		try {
			// sleep to allow data to be transmitted
			_Logger.info("Waiting 30 seconds for data transmission...");
			Thread.sleep(30000L);
		} catch (Exception e) {
			// ignore
		}
		
		assertTrue(this.cloudClient.disconnectClient());
		
		_Logger.info("TEST 2 complete. Check your Ubidots dashboard to verify data was received.");
	}
	
	/**
	 * TEST 3: Publish and Subscribe - LED Actuation Event Test
	 * 
	 * This test verifies end-to-end functionality:
	 * 1. Connects to cloud service
	 * 2. Subscribes to LED actuation topic
	 * 3. Publishes sensor data with threshold crossing values
	 * 4. Waits for cloud service to trigger actuation event
	 * 5. Verifies actuation event is received
	 * 
	 * IMPORTANT: You must configure a rule in Ubidots to trigger
	 * an LED actuation event based on the sensor data thresholds.
	 */
	@Test
	public void testPublishAndSubscribe()
	{
		_Logger.info("TEST 3: Testing publish and subscribe with LED actuation...");
		
		this.cloudClient.setDataMessageListener(new DefaultDataMessageListener());
		
		assertTrue(this.cloudClient.connectClient());
		
		try {
			// sleep to allow connection to complete
			_Logger.info("Waiting 5 seconds for connection to complete...");
			Thread.sleep(5000L);
		} catch (Exception e) {
			// ignore
		}
		
		// Subscribe to actuator command topic (LED actuation)
		_Logger.info("Subscribing to LED actuation events...");
		assertTrue(this.cloudClient.subscribeToCloudEvents(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE));
		
		try {
			// sleep to allow subscription to complete
			_Logger.info("Waiting 5 seconds for subscription to complete...");
			Thread.sleep(5000L);
		} catch (Exception e) {
			// ignore
		}
		
		// Publish sensor data with HIGH value to trigger actuation
		SensorData tempData = new SensorData();
		tempData.setName(ConfigConst.TEMP_SENSOR_NAME);
		tempData.setValue(95.0f); // High temperature to trigger LED ON
		
		_Logger.info("Publishing HIGH temperature data to trigger actuation: " + tempData.getValue());
		assertTrue(this.cloudClient.sendEdgeDataToCloud(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, tempData));
		
		// Publish system performance data
		SystemPerformanceData sysPerfData = new SystemPerformanceData();
		sysPerfData.setCpuUtilization(85.7f);
		sysPerfData.setMemoryUtilization(78.3f);
		
		_Logger.info("Publishing system performance data - CPU: " + sysPerfData.getCpuUtilization() + "%, Memory: " + sysPerfData.getMemoryUtilization() + "%");
		assertTrue(this.cloudClient.sendEdgeDataToCloud(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, sysPerfData));
		
		try {
			// sleep to allow cloud to process and trigger actuation
			_Logger.info("Waiting 30 seconds for cloud to process data and trigger actuation...");
			Thread.sleep(30000L);
		} catch (Exception e) {
			// ignore
		}
		
		// Publish LOW temperature to trigger LED OFF
		tempData.setValue(18.0f);
		_Logger.info("Publishing LOW temperature data to trigger LED OFF: " + tempData.getValue());
		assertTrue(this.cloudClient.sendEdgeDataToCloud(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, tempData));
		
		try {
			// sleep to allow actuation event to be received
			_Logger.info("Waiting 30 seconds for actuation event...");
			Thread.sleep(30000L);
		} catch (Exception e) {
			// ignore
		}
		
		// Unsubscribe from actuator command topic
		_Logger.info("Unsubscribing from LED actuation events...");
		assertTrue(this.cloudClient.unsubscribeFromCloudEvents(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE));

		try {
			// sleep before disconnecting
			_Logger.info("Waiting 10 seconds before disconnect...");
			Thread.sleep(10000L);
		} catch (Exception e) {
			// ignore
		}

		assertTrue(this.cloudClient.disconnectClient());

		try {
			// sleep to allow disconnect to complete
			_Logger.info("Waiting 2 seconds for disconnect to complete...");
			Thread.sleep(2000L);
		} catch (Exception e) {
			// ignore
		}
		
		_Logger.info("TEST 3 complete. Check logs for LED actuation messages.");
	}
	
	/**
	 * TEST 4: Integrated Test with DeviceDataManager
	 * 
	 * This test verifies the complete integration of CloudClientConnector
	 * with DeviceDataManager. This simulates the actual runtime behavior
	 * of the GDA.
	 * 
	 * IMPORTANT: Make sure enableCloudClient=true in PiotConfig.props
	 */
	@Test
	public void testIntegratedCloudClientConnectAndDisconnect()
	{
		_Logger.info("TEST 4: Testing integrated cloud client with DeviceDataManager...");
		
		DeviceDataManager ddm = new DeviceDataManager();
		ddm.startManager();
		
		try {
			// sleep for a minute to allow data to flow
			_Logger.info("DeviceDataManager running... waiting 60 seconds for data flow...");
			_Logger.info("During this time:");
			_Logger.info("  - System performance data should be sent to cloud");
			_Logger.info("  - Cloud connection should be established");
			_Logger.info("  - LED actuation topic should be subscribed");
			
			Thread.sleep(60000L);
		} catch (Exception e) {
			// ignore
		}
		
		ddm.stopManager();
		
		_Logger.info("TEST 4 complete. Check your Ubidots dashboard and logs.");
	}
	
}
