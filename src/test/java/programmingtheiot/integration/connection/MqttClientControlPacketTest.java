/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */ 

package programmingtheiot.integration.connection;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.*;
import programmingtheiot.gda.connection.*;

/**
 * Test case class to generate all 14 MQTT 3.1.1 Control Packets.
 * 
 * This test suite demonstrates:
 * - Connection establishment and termination
 * - Keep-alive ping mechanism
 * - Publish/Subscribe with QoS 0, 1, and 2
 * - All MQTT control packet types
 * 
 * Control Packets Generated:
 * 1. CONNECT
 * 2. CONNACK
 * 3. PUBLISH
 * 4. PUBACK (QoS 1)
 * 5. PUBREC (QoS 2)
 * 6. PUBREL (QoS 2)
 * 7. PUBCOMP (QoS 2)
 * 8. SUBSCRIBE
 * 9. SUBACK
 * 10. UNSUBSCRIBE
 * 11. UNSUBACK
 * 12. PINGREQ
 * 13. PINGRESP
 * 14. DISCONNECT
 */
public class MqttClientControlPacketTest
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(MqttClientControlPacketTest.class.getName());
	
	
	// member var's
	
	private MqttClientConnector mqttClient = null;
	
	
	// test setup methods
	
	/**
	 * Set up test environment before each test.
	 * Creates a new MQTT client instance with unique client ID.
	 */
	@Before
	public void setUp() throws Exception
	{
		_Logger.info("=================================================");
		_Logger.info("Executing MqttClientControlPacketTest...");
		_Logger.info("This test will generate all 14 MQTT Control Packets");
		_Logger.info("=================================================");
		
		// Use default constructor - client ID will come from config file
		this.mqttClient = new MqttClientConnector();
		
		_Logger.info("Test MQTT Client initialized successfully");
	}
	
	/**
	 * Clean up after each test.
	 * Ensures MQTT client is properly disconnected.
	 */
	@After
	public void tearDown() throws Exception
	{
		try {
			if (this.mqttClient != null) {
				_Logger.info("Cleaning up: Disconnecting MQTT client...");
				this.mqttClient.disconnectClient();
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			_Logger.warning("Teardown exception (safe to ignore): " + e.getMessage());
		}
	}
	
	// test methods
	
	/**
	 * Test 1: Basic Connection and Disconnection
	 * 
	 * Control Packets Generated:
	 * 1. CONNECT - Client initiates connection to broker
	 * 2. CONNACK - Broker acknowledges connection
	 * 3. DISCONNECT - Client gracefully disconnects
	 * 
	 * This is the foundation for all MQTT communication.
	 */
	@Test
	public void testConnectAndDisconnect()
	{
		_Logger.info("\n============================================================");
		_Logger.info("TEST 1: Connect and Disconnect");
		_Logger.info("============================================================");
		_Logger.info("Expected Control Packets: CONNECT, CONNACK, DISCONNECT");
		
		try {
			// Generate CONNECT → CONNACK
			_Logger.info("Step 1: Connecting to MQTT broker...");
			assertTrue("Failed to connect to MQTT broker", this.mqttClient.connectClient());
			
			// Wait to ensure connection is established
			Thread.sleep(3000);
			_Logger.info("Step 2: Connection established. Waiting before disconnect...");
			
			// Generate DISCONNECT
			Thread.sleep(2000);
			_Logger.info("Step 3: Disconnecting from MQTT broker...");
			assertTrue("Failed to disconnect from MQTT broker", this.mqttClient.disconnectClient());
			
			Thread.sleep(1000);
			_Logger.info("✓ Test completed successfully");
			_Logger.info("============================================================\n");
			
		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			fail("Test threw exception: " + e.getMessage());
		}
	}
	
	/**
	 * Test 2: Keep-Alive Mechanism (Server Ping)
	 * 
	 * Control Packets Generated:
	 * 12. PINGREQ - Client sends keep-alive ping
	 * 13. PINGRESP - Broker responds to ping
	 * 
	 * The client automatically sends PINGREQ after the keep-alive interval
	 * (default 60 seconds) to maintain the connection. The broker responds
	 * with PINGRESP to acknowledge it's still alive.
	 * 
	 * NOTE: This test takes ~70 seconds to complete as it must wait for
	 * the keep-alive timeout to trigger the ping mechanism.
	 */
	@Test
	public void testServerPing()
	{
		_Logger.info("\n============================================================");
		_Logger.info("TEST 2: Keep-Alive PING Mechanism");
		_Logger.info("============================================================");
		_Logger.info("Expected Control Packets: PINGREQ, PINGRESP");
		_Logger.info("NOTE: This test takes ~70 seconds to allow keep-alive to trigger");
		
		try {
			// Connect to broker
			_Logger.info("Step 1: Connecting to MQTT broker...");
			assertTrue("Failed to connect to MQTT broker", this.mqttClient.connectClient());
			Thread.sleep(2000);
			
			// Get keep-alive interval from configuration
			ConfigUtil configUtil = ConfigUtil.getInstance();
			int keepAliveInterval = configUtil.getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE, 
				ConfigConst.KEEP_ALIVE_KEY, 
				ConfigConst.DEFAULT_KEEP_ALIVE);
			
			int waitTime = keepAliveInterval + 10; // Wait slightly longer than keep-alive
			
			_Logger.info("Step 2: Keep-Alive interval is " + keepAliveInterval + " seconds");
			_Logger.info("Step 3: Waiting " + waitTime + " seconds for PINGREQ/PINGRESP...");
			_Logger.info("         (The client will automatically send PINGREQ)");
			
			// Count down for user visibility
			for (int remaining = waitTime; remaining > 0; remaining -= 10) {
				_Logger.info("         ... " + remaining + " seconds remaining ...");
				Thread.sleep(10000);
			}
			
			_Logger.info("Step 4: Keep-alive ping should have occurred!");
			Thread.sleep(2000);
			
			// Disconnect
			_Logger.info("Step 5: Disconnecting from MQTT broker...");
			assertTrue("Failed to disconnect from MQTT broker", this.mqttClient.disconnectClient());
			
			Thread.sleep(1000);
			_Logger.info("✓ Test completed successfully");
			_Logger.info("============================================================\n");
			
		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			fail("Test threw exception: " + e.getMessage());
		}
	}
	
	/**
	 * Test 3: Complete Publish/Subscribe Workflow
	 * 
	 * Control Packets Generated:
	 * 8. SUBSCRIBE - Subscribe to topic
	 * 9. SUBACK - Broker acknowledges subscription
	 * 3. PUBLISH (QoS 0) - Publish with no acknowledgment
	 * 3. PUBLISH (QoS 1) - Publish with acknowledgment
	 * 4. PUBACK - Broker acknowledges QoS 1 publish
	 * 3. PUBLISH (QoS 2) - Publish with assured delivery
	 * 5. PUBREC - Broker receives QoS 2 publish (step 1 of 4-way handshake)
	 * 6. PUBREL - Client releases QoS 2 publish (step 2 of 4-way handshake)
	 * 7. PUBCOMP - Broker completes QoS 2 publish (step 3 of 4-way handshake)
	 * 10. UNSUBSCRIBE - Unsubscribe from topic
	 * 11. UNSUBACK - Broker acknowledges unsubscription
	 * 
	 * This test demonstrates all QoS levels and the complete
	 * publish/subscribe workflow.
	 * 
	 * IMPORTANT: Uses QoS 1 and 2 to see ALL control packets
	 */
	@Test
	public void testPubSub()
	{
		_Logger.info("\n============================================================");
		_Logger.info("TEST 3: Publish/Subscribe with All QoS Levels");
		_Logger.info("============================================================");
		_Logger.info("Expected Control Packets:");
		_Logger.info("  - SUBSCRIBE, SUBACK");
		_Logger.info("  - PUBLISH (QoS 0)");
		_Logger.info("  - PUBLISH (QoS 1), PUBACK");
		_Logger.info("  - PUBLISH (QoS 2), PUBREC, PUBREL, PUBCOMP");
		_Logger.info("  - UNSUBSCRIBE, UNSUBACK");
		
		try {
			// Get DataUtil singleton instance - CORRECT WAY
			DataUtil dataUtil = DataUtil.getInstance();
			
			// Step 1: Connect
			_Logger.info("\nStep 1: Connecting to MQTT broker...");
			assertTrue("Failed to connect to MQTT broker", this.mqttClient.connectClient());
			Thread.sleep(2000);
			
			// Step 2: Subscribe - Generates SUBSCRIBE, SUBACK
			_Logger.info("Step 2: Subscribing to management status topic...");
			_Logger.info("        Generates: SUBSCRIBE → SUBACK");
			assertTrue("Failed to subscribe", 
				this.mqttClient.subscribeToTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, 1));
			Thread.sleep(2000);
			
			// Step 3: Publish with QoS 0 - Generates PUBLISH only
			_Logger.info("Step 3: Publishing ActuatorData with QoS 0...");
			_Logger.info("        Generates: PUBLISH (no acknowledgment)");
			
			ActuatorData actuatorData0 = new ActuatorData();
			actuatorData0.setName("HVAC Actuator");
			actuatorData0.setCommand(1);
			actuatorData0.setValue(20.0f);
			
			String payload0 = dataUtil.actuatorDataToJson(actuatorData0);
			
			assertTrue("Failed to publish QoS 0 message",
				this.mqttClient.publishMessage(
					ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, 
					payload0, 
					0)); // QoS 0: At most once (fire and forget)
			Thread.sleep(2000);
			
			// Step 4: Publish with QoS 1 - Generates PUBLISH, PUBACK
			_Logger.info("Step 4: Publishing ActuatorData with QoS 1...");
			_Logger.info("        Generates: PUBLISH → PUBACK");
			
			ActuatorData actuatorData1 = new ActuatorData();
			actuatorData1.setName("LED Actuator");
			actuatorData1.setCommand(2);
			actuatorData1.setValue(100.0f);
			
			String payload1 = dataUtil.actuatorDataToJson(actuatorData1);
			
			assertTrue("Failed to publish QoS 1 message",
				this.mqttClient.publishMessage(
					ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE, 
					payload1, 
					1)); // QoS 1: At least once
			Thread.sleep(3000);
			
			// Step 5: Publish with QoS 2 - Generates PUBLISH, PUBREC, PUBREL, PUBCOMP
			_Logger.info("Step 5: Publishing SensorData with QoS 2...");
			_Logger.info("        Generates: PUBLISH → PUBREC → PUBREL → PUBCOMP");
			_Logger.info("        (4-way handshake for assured delivery)");
			
			SensorData sensorData = new SensorData();
			sensorData.setName("Temperature Sensor");
			sensorData.setValue(23.5f);
			
			String payload2 = dataUtil.sensorDataToJson(sensorData);
			
			assertTrue("Failed to publish QoS 2 message",
				this.mqttClient.publishMessage(
					ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, 
					payload2, 
					2)); // QoS 2: Exactly once (assured delivery)
			Thread.sleep(5000); // Extra time for 4-way handshake to complete
			
			// Step 6: Publish SystemPerformanceData with QoS 2
			_Logger.info("Step 6: Publishing SystemPerformanceData with QoS 2...");
			_Logger.info("        Generates: Another PUBLISH → PUBREC → PUBREL → PUBCOMP");
			
			SystemPerformanceData sysPerfData = new SystemPerformanceData();
			sysPerfData.setCpuUtilization(45.2f);
			sysPerfData.setMemoryUtilization(62.8f);
			
			String payload3 = dataUtil.systemPerformanceDataToJson(sysPerfData);
			
			assertTrue("Failed to publish QoS 2 message",
				this.mqttClient.publishMessage(
					ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE, 
					payload3, 
					2));
			Thread.sleep(5000);
			
			// Step 7: Unsubscribe - Generates UNSUBSCRIBE, UNSUBACK
			_Logger.info("Step 7: Unsubscribing from management status topic...");
			_Logger.info("        Generates: UNSUBSCRIBE → UNSUBACK");
			
			assertTrue("Failed to unsubscribe",
				this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE));
			Thread.sleep(2000);
			
			// Step 8: Disconnect
			_Logger.info("Step 8: Disconnecting from MQTT broker...");
			assertTrue("Failed to disconnect from MQTT broker", 
				this.mqttClient.disconnectClient());
			
			Thread.sleep(1000);
			_Logger.info("✓ Test completed successfully");
			_Logger.info("============================================================\n");
			
		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			e.printStackTrace();
			fail("Test threw exception: " + e.getMessage());
		}
	}
}