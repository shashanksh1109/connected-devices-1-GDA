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

package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;

import programmingtheiot.common.IDataMessageListener;

/**
 * Observer handler for SystemPerformanceData updates via CoAP OBSERVE.
 *
 */
public class SystemPerformanceDataObserverHandler implements CoapHandler
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(SystemPerformanceDataObserverHandler.class.getName());
	
	// params
	
	private IDataMessageListener dataMsgListener;
	
	// constructors
	
	/**
	 * Constructor.
	 * 
	 */
	public SystemPerformanceDataObserverHandler()
	{
		super();
	}
	
	// public methods
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
		this.dataMsgListener = listener;
	}
	
	@Override
	public void onError()
	{
		_Logger.warning("Handling CoAP error...");
	}

	@Override
	public void onLoad(CoapResponse response)
	{
		_Logger.info("Received CoAP response (payload should be SystemPerformanceData in JSON): " + response.getResponseText());
	}
	
}