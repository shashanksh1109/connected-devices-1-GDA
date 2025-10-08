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

package programmingtheiot.data;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;

/**
 * Shell representation of class for student implementation.
 *
 */
public class DataUtil
{
	// static
	
	private static final DataUtil _Instance = new DataUtil();

	/**
	 * Returns the Singleton instance of this class.
	 * 
	 * @return ConfigUtil
	 */
	public static final DataUtil getInstance()
	{
		return _Instance;
	}
	
	
	// private var's
	
	private Gson gson = null;
	
	
	// constructors
	
	/**
	 * Default (private).
	 * 
	 */
	private DataUtil()
	{
		super();
		
		this.gson = new Gson();
	}
	
	
	// public methods
	
	public String actuatorDataToJson(ActuatorData actuatorData)
	{
		String jsonData = null;
		
		if (actuatorData != null) {
			jsonData = this.gson.toJson(actuatorData);
		}
		
		return jsonData;
	}
	
	public String actuatorDataToTimeAndValueJson(ActuatorData actuatorData)
	{
		return null;
	}
	
	public String sensorDataToJson(SensorData sensorData)
	{
		String jsonData = null;
		
		if (sensorData != null) {
			jsonData = this.gson.toJson(sensorData);
		}
		
		return jsonData;
	}
	
	public String sensorDataToTimeAndValueJson(SensorData sensorData)
	{
		return null;
	}
	
	public String systemPerformanceDataToJson(SystemPerformanceData sysPerfData)
	{
		String jsonData = null;
		
		if (sysPerfData != null) {
			jsonData = this.gson.toJson(sysPerfData);
		}
		
		return jsonData;
	}
	
	public String systemStateDataToJson(SystemStateData sysStateData)
	{
		String jsonData = null;
		
		if (sysStateData != null) {
			jsonData = this.gson.toJson(sysStateData);
		}
		
		return jsonData;
	}
	
	public ActuatorData jsonToActuatorData(String jsonData)
	{
		ActuatorData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			data = this.gson.fromJson(jsonData, ActuatorData.class);
		}
		
		return data;
	}
	
	public SensorData jsonToSensorData(String jsonData)
	{
		SensorData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			data = this.gson.fromJson(jsonData, SensorData.class);
		}
		
		return data;
	}
	
	public SystemPerformanceData jsonToSystemPerformanceData(String jsonData)
	{
		SystemPerformanceData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			data = this.gson.fromJson(jsonData, SystemPerformanceData.class);
		}
		
		return data;
	}
	
	public SystemStateData jsonToSystemStateData(String jsonData)
	{
		SystemStateData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			data = this.gson.fromJson(jsonData, SystemStateData.class);
		}
		
		return data;
	}
	
}