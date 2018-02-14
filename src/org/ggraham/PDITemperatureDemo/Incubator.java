package org.ggraham.PDITemperatureDemo;

// Simple "Incubator" model for chicks.  
// 
//   State of the incubator - heatlamp can be on, off, or broken; 
//   and there is a setting for the ambient temperature. If the 
//   heatlamp is on, then the setpoint is the lamp temperature.  If
//   the heatlamp is off, the setpoint is ambient temperature. 
public class Incubator extends TemperatureResponse {

	public static final double D_HEATLAMP_TEMPERATURE = 40.0d; // Celsius 
	
	private boolean m_heatlampOn;
    private boolean m_heatlampBroken;
    private double m_ambientTemperature;
    
	public void turnOnHeatlamp() {
		m_heatlampOn = true;
		if ( !m_heatlampBroken ) {
	    	setTemperatureSetpoint(D_HEATLAMP_TEMPERATURE);
		}
		else {
			setTemperatureSetpoint(m_ambientTemperature);
		}
	}

	public void turnOffHeatlamp() {
		m_heatlampOn = false;
		setTemperatureSetpoint(m_ambientTemperature);
	}
	
	public void breakHeatlamp() {
		m_heatlampBroken = true;
		setTemperatureSetpoint(m_ambientTemperature);
	}
	
	public void fixHeatlamp() {
		m_heatlampBroken = false;
	}

	public int getHeatlampState() {
		return m_heatlampOn ? 1 : 0;
	}
	
	public void setAmbientTemperature(double temp) {
		m_ambientTemperature = temp;
		if ( ! m_heatlampOn || m_heatlampBroken ) {
			setTemperatureSetpoint(m_ambientTemperature);
		}
	}
	
	public double getAmbientTemperature() {
		return m_ambientTemperature;
	}
	
	public Incubator(double initialTemp, double halflife, double ambientTemperature) {
		super(initialTemp, halflife);	
		m_heatlampBroken = false;
		m_heatlampOn = false;
		m_ambientTemperature = ambientTemperature;
	}

	
	
}
