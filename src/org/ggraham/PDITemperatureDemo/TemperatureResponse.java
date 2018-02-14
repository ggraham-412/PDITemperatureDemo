package org.ggraham.PDITemperatureDemo;

import javax.naming.InitialContext;

//  Models a temperature system that has a current temperature and 
//  a setpoint.  The temperature approaches the setpoint every time
//  step() is called with a halflife of N steps.
public class TemperatureResponse {

	private double m_temperature;
	private double m_temperatureSet;
	private double m_response;
	
	public double getTemperature() {
		return m_temperature;
	}

	public void step() {
		m_temperature += m_response * (m_temperatureSet - m_temperature);
	}
	
	public double getTemperatureSetpoint() {
		return m_temperatureSet;
	}
	public void setTemperatureSetpoint(double newTemp) {
		m_temperatureSet = newTemp;
	}
	
	public TemperatureResponse(double initialTemp, double halflife) {
		m_temperature = initialTemp;
		m_temperatureSet = initialTemp;		
		if ( halflife <= 0.0 ) {
			throw new IllegalArgumentException("Half life must be > 0 steps");
		}
		m_response = 1.0d - Math.pow(Math.E, -Math.log(2.0d)/halflife);
	}
	
}
