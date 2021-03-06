package de.unisiegen.sensortools.cluster.sensors;

import de.unisiegen.sensortools.Location;
import net.sf.javaml.core.DenseInstance;

import java.util.Collection;
import java.util.Map;


public class UserLocation extends AbstractMeasurement implements Comparable<UserLocation>{

    private Location loc;
    private long date;
    private static final long serialVersionUID = -7202442547223420283L;
	private long parentCluster;
	
	public UserLocation(Location loc, long date, int parentCluster){
        super(getDoubleArray(loc));
        this.loc = loc;
        this.date = date;
        this.parentCluster = parentCluster;
        this.setStart(date);
	}

	public long getParentCluster() {
		return parentCluster;
	}

	public void setParentCluster(long parentCluster) {
		this.parentCluster = parentCluster;
	}
	public int compareTo(UserLocation cl2) {
		
		return new Long(getDate()).compareTo(new Long(cl2.getDate()));
	
	}
	
	public boolean equals(Object obj) {
		if(obj instanceof UserLocation){
			return ((UserLocation)obj).compareTo(this)==0;
		}
		return false;
	}

    public Location getLoc() {
        return loc;
    }

    public void setLoc(Location loc) {
        this.loc = loc;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }


    private static double[] getDoubleArray(Location loc){
        double [] tmp = new double[2];
        tmp[0] = (double)loc.lat/1000000.0;
        tmp[1] = (double)loc.lon/1000000.0;
        return tmp;
    }


    
}
