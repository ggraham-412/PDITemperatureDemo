package org.ggraham.PDITemperatureDemo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

import org.ggraham.ggutils.message.IHandleMessage;
import org.ggraham.ggutils.message.PacketDecoder;
import org.ggraham.ggutils.network.UDPReceiver;
import org.ggraham.ggutils.network.UDPSender;
import org.ggraham.ggutils.objectpool.ObjectPool.PoolItem;

//  A multi-threaded model of 10 incubators in a room with a door that can 
//  open and close disturbing the ambient temperature.  Each incubator has 
//  a heatlamp which can be on or off depending on whether or not they are 
//  too warm or too cold. Time is modeled relative to the start time of the 
//  application, but proceeds at a pace 10x real time for convenience.
public class TemperatureDemo {

	// Config constants
	public static final int N_INCUBATORS = 10;
	public static final double D_ROOM_TEMPERATURE = 25.0d; // Celsius
	public static final double D_OUTSIDE_TEMPERATURE_1 = 0.0d; // Celsius
	public static final double D_OUTSIDE_TEMPERATURE_2 = 15.0d; // Celsius
	public static final double D_HALFLIFE = 90.0d; // seconds 
	public static final int N_MILLISECONDS_PER_STEP = 100; // Every 100 mSec
	public static final int N_MILLISECONDS_PER_READING = 10000; // Every 10 sec

	// Data 
	private static final Incubator[] s_incubators = new Incubator[N_INCUBATORS];
	private static final Object s_lock = new Object(); 
	private static final Queue<ControlRecord> s_controlQueue = new LinkedList<ControlRecord>();
	private static long s_currentTime;
	private static boolean s_eventLoop = true;
	private static long s_lastReadingTime = 0;
	
	// UDP sockets to send/receive data to/from PDI
    private static UDPSender s_sender = new UDPSender("localhost", 5555, 1024, 1024);
    private static PacketDecoder s_decoder = new PacketDecoder();
    private static ByteBuffer s_buffer = ByteBuffer.allocate(1536);
    private static UDPReceiver s_receiver = new UDPReceiver("localhost", 6666,true,  new CommandHandler());

    // Converts control messages coming in from PDI and puts them in a queue.
    private static class CommandHandler implements IHandleMessage<ByteBuffer> {
    	@Override
    	public boolean handleMessage(ByteBuffer message) {
    		// We can ignore the time coming in from PDI; we'll handle the 
    		// message immediately; but we have to pop it off the message.
    		message.getLong();
    		ControlRecord c = new ControlRecord(s_currentTime, 
    				                            message.getInt(), // The command code
    				                            message.getInt());  // The sensor ID
    	    synchronized (s_lock) {
    	    	s_controlQueue.add(c);				
			}
    	    return true;
    	}
    }
    		
    // A control record 
    // PDI can send heatlamp instructions, the other instructions are used 
    // to simulate events.
    private static final class ControlRecord {
		public static final int S_HEATLAMP_ON = 1;
		public static final int S_HEATLAMP_OFF = 2;
		public static final int S_DOOR_OPEN = 3;
		public static final int S_DOOR_CLOSED = 4;
		public static final int S_BUST_HEATLAMP = 5;
		public static final int S_FIX_HEATLAMP = 6;
		
		private final int m_command;
		private final int m_id;
		private final long m_time;
		private final String m_str;
		
		public long getTime() {
			return m_time;
		}
		
		public int getCommand() {
			return m_command;
		}

		public int getId() {
			return m_id;
		}

		@Override
		public String toString() {
			return m_str;
		}
		
		public static ControlRecord HeatlampOn(long time, int id) {
			return new ControlRecord(time, S_HEATLAMP_ON, id);
		}
		public static ControlRecord HeatlampOff(long time, int id) {
			return new ControlRecord(time, S_HEATLAMP_OFF, id);
		}
		public static ControlRecord DoorOpen(long time) {
			return new ControlRecord(time, S_DOOR_OPEN, -1);
		}
		public static ControlRecord DoorClosed(long time) {
			return new ControlRecord(time, S_DOOR_CLOSED, -1);
		}
		public static ControlRecord BustHeatlamp(long time, int id) {
			return new ControlRecord(time, S_BUST_HEATLAMP, id);
		}
		public static ControlRecord FixHeatlamp(long time, int id) {
			return new ControlRecord(time, S_FIX_HEATLAMP, id);
		}
		
		private ControlRecord(long time, int command, int id) {
			m_time = time;
			m_command = command;
			m_id = id;
			m_str = "(" + (new Date(m_time)).toString() + "," + m_id + "," + m_command + ")"; 
		}
	}
	
    // Simulates opening the door.  The door is located at one end of a row 
    // of incubator, and we model a linear drop in ambient temperature depending
    // on how far away the door is while the door is open
	public static void openDoor() {
		for ( int i = 0; i < s_incubators.length; i++ ) {
			s_incubators[i].setAmbientTemperature( D_OUTSIDE_TEMPERATURE_1 + 
			    (D_OUTSIDE_TEMPERATURE_2 - D_OUTSIDE_TEMPERATURE_1) * i / s_incubators.length);
		}
	}

	// We reset ambient temperatures to room temperature.
	public static void closeDoor() {
		for ( int i = 0; i < s_incubators.length; i++ ) {
			s_incubators[i].setAmbientTemperature(D_ROOM_TEMPERATURE);
		}
	}

	// Processes all incubators through a time step
	public static void doIncubatorSteps() {
		for ( int i = 0; i < s_incubators.length; i++ ) {
			s_incubators[i].step();
		}
	}

	// This writes current temperature and heatlamp status to PDI
	public static void dumpReadings() {
		PoolItem<ByteBuffer> item = s_sender.getByteBuffer();
		for ( int i = 0; i < s_incubators.length; i++ ) {
			s_decoder.EncodePacket(new Object[] {s_currentTime, i, s_incubators[i].getTemperature(), s_incubators[i].getHeatlampState()}, 
					item.getPoolItem());
		}
        s_sender.send(item);		
	}

	//  This processes a command record from PDI or from the simulation
    public static void processCommand(ControlRecord record) {
    	switch(record.getCommand()) {
    	case ControlRecord.S_BUST_HEATLAMP:
    		s_incubators[record.getId()].breakHeatlamp();
    		break;
    	case ControlRecord.S_FIX_HEATLAMP:
    		s_incubators[record.getId()].fixHeatlamp();
    		break;
    	case ControlRecord.S_HEATLAMP_ON:
    		s_incubators[record.getId()].turnOnHeatlamp();
    		break;
    	case ControlRecord.S_HEATLAMP_OFF:
    		s_incubators[record.getId()].turnOffHeatlamp();
    		break;
    	case ControlRecord.S_DOOR_OPEN:
    		openDoor();
    		break;
    	case ControlRecord.S_DOOR_CLOSED:
    		closeDoor();
    		break;
    	}
    }
	
    // Process the next time step
	public static void nextStep() {
		// Increment time
		s_currentTime += N_MILLISECONDS_PER_STEP;

		// Check for post-dated control records for the simulation
		ControlRecord record = null;
		ArrayList<ControlRecord> toRemove = new ArrayList<ControlRecord>();
		synchronized(s_lock) {
			for ( ControlRecord cr : s_controlQueue ) {
				if ( cr != null && cr.getTime() < s_currentTime ) {
						processCommand(cr);	
						toRemove.add(cr);
				}
			}
			for ( ControlRecord cr : toRemove ) {
				s_controlQueue.remove(cr);
			}			
		}
		
		// Step the incubators forward
		doIncubatorSteps();

		// Check if it is time to send readings to PDI
		if ( s_lastReadingTime + N_MILLISECONDS_PER_READING < s_currentTime ) {
			dumpReadings();			
			s_lastReadingTime = s_currentTime;
		}
	}

	// Main application
	public static void main(String[] args) {

		// Set all incubators to room temp
		for ( int i = 0; i < N_INCUBATORS; i++ ) {
			s_incubators[i] = new Incubator(D_ROOM_TEMPERATURE, 
					                      D_HALFLIFE * 1000 / N_MILLISECONDS_PER_STEP,
					                      D_ROOM_TEMPERATURE);
		}

		// Set the initial time
        s_currentTime = (new Date()).getTime();
        
        // Add post-dated control records to the control queue
        // After 5 minutes, open and close the door.
        s_controlQueue.add(ControlRecord.DoorOpen(s_currentTime + 300 * 1000));
        s_controlQueue.add(ControlRecord.DoorClosed(s_currentTime + 330 * 1000));
        
        // After 6 minutes, break heatlamp on #5
        s_controlQueue.add(ControlRecord.BustHeatlamp(s_currentTime + 360 * 1000, 5));
        
        // After 10 minutes, open and close the door
        s_controlQueue.add(ControlRecord.DoorOpen(s_currentTime + 600 * 1000));
        s_controlQueue.add(ControlRecord.DoorClosed(s_currentTime + 630 * 1000));
        
        // After 11 minutes, fix the heat lamp 
        s_controlQueue.add(ControlRecord.FixHeatlamp(s_currentTime + 660 * 1000, 5));
        
        //  After 15 minutes, open and close the door
        s_controlQueue.add(ControlRecord.DoorOpen(s_currentTime + 900 * 1000));
        s_controlQueue.add(ControlRecord.DoorClosed(s_currentTime + 930 * 1000));
        s_receiver.start();

        // Record structure of what we send to PDI
        s_decoder.addLong();  // Timestamp
        s_decoder.addInt();   // ID
        s_decoder.addDouble();  // Temp
        s_decoder.addInt();   // Heatlamp state

        // Start a thread to run the simulation
		Thread t1 = new Thread() {
			@Override 
			public void run() {
				while ( s_eventLoop ) {
    				nextStep();
    				try {
    					// Pause for 10 mS, so that the simulation runs in 10x real time.
						Thread.currentThread().sleep(10);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};
		t1.start();
		
		// After 100 seconds of real time (1000 sec of simulated time, 
		// stop the simulation and join.
		try {
			Thread.sleep(100000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        s_eventLoop = false;
		try {
			t1.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}

}
