package sics.port.instances;

import com.sun.squawk.VM;

import sics.PIRTE;
import sics.port.EcuVirtualPPort;
import sics.port.EcuVirtualRPort;

//TODO: Make this class generic (so that data doesn't need casting)
public class VirtualRearWheelPPort extends EcuVirtualPPort {
	private int id;
	
	public VirtualRearWheelPPort(int id) {
		super(id);
	}

	@Override
	public Object deliver() {
		return new Integer(VM.jnaFetchBackWheelSpeed()); 
	}

	@Override
	public Object deliver(int portId) {
		// TODO Auto-generated method stub
		return null;
	}

}
