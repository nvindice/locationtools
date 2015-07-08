package de.unisiegen.locationtools.cluster;


import android.util.Log;


import com.google.gson.Gson;

import de.unisiegen.locationtools.Location;
import net.sf.javaml.clustering.DensityBasedSpatialClustering;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.Instance;

import org.json.JSONArray;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Created by Martin on 05.03.2015.
 */
public class ClusterManagement {
    private static double minLocsFactor = 0.03;
    private static int distance4Clustering = 25;
    private static Dataset[] resultsCluster;
    private static HashMap<Long, HashMap<Long, Double>> probresult = null;

    public static HashMap<Location, Dataset> clusterLocations(Date since, Date until, Integer max, boolean onlyUnClustered) {
        HashMap<Location, Dataset> clusterCenters = new HashMap<Location, Dataset>();

        Utilities.openDBConnection();

        Dataset data = new DefaultDataset();
        Log.d("PTEnabler", "Initializing Locations for clustering\nSince:" + since.toLocaleString() + "\nUntil:" + until.toLocaleString());
        List<UserLocation> locs;
        if (onlyUnClustered) {
            locs = Utilities.openDBConnection().getUnclusteredHistoryLocs(since.getTime(), until.getTime());
        } else {
            locs = Utilities.openDBConnection().getAllHistoryLocs(since.getTime(), until.getTime());
        }

        Log.d("PTEnabler", "Number of Locations available for clustering:" + locs.size());

        if (max != null) {
            int allLocs = locs.size();
            int every = (allLocs > max) ? allLocs / max : 1;
            int i = 0;
            for (UserLocation loc : locs) {
                if ((i++) % every == 0) {
//							double[] x= new double[]{((double)(loc.getLoc().lat))/1000000,((double)(loc.getLoc().lon))/1000000};
//							Instance instance = new DenseInstance(x);
                    data.add(loc);
                } else {
                    continue;
                }
            }
        } else {
            for (UserLocation loc : locs) {
//					double[] x= new double[]{((double)(loc.getLoc().lat))/1000000,((double)(loc.getLoc().lon))/1000000};
//					Instance instance = new DenseInstance(x);
                data.add(loc);
            }
        }

        Log.d("PTEnabler", "" + data.size() + " Locations initialized for clustering");
        int minLocs = Math.max(10, (int) (data.size() * minLocsFactor));
        if (onlyUnClustered) {
            minLocs *= 3;
        }
        Log.d("PTEnabler", "" + minLocs + " Locations required for clustering");
        DensityBasedSpatialClustering clusterer = new DensityBasedSpatialClustering(distance4Clustering, minLocs, new MyDistanceMeasure());
        Log.d("PTEnabler", "Starting Clustering");

        resultsCluster = clusterer.cluster(data);


        Log.d("PTEnabler", "Finished Clustering");
        for (Dataset y : resultsCluster) {
            double sumLat = 0.0;
            double sumLon = 0.0;
            Iterator<Instance> it = y.listIterator();
            while (it.hasNext()) {
                Instance i = it.next();
                if (i instanceof UserLocation) {
                    UserLocation uloc = (UserLocation) i;
                    sumLat += ((double) uloc.getLoc().lat) / 1000000.0;
                    sumLon += ((double) uloc.getLoc().lon) / 1000000.0;
                }


            }
            int lat = (int) ((sumLat / (double) y.size()) * 1000000.0);
            int lon = (int) ((sumLon / (double) y.size()) * 1000000.0);
            clusterCenters.put(new Location(LocationType.ADDRESS, lat, lon), y);
        }

        return clusterCenters;
    }

    public static HashMap<Location, Dataset> clusterLocations(Date since, Date until, Integer max) {
        return clusterLocations(since, until, max, false);
    }

    public static List<ClusteredLocation> getClusteredLocationsFromCache() {
        return Utilities.openDBConnection().getAllClusterLocs();
    }
    public static List<ClusteredLocation> getClusteredLocationWithIDs(Collection<Long> ids){
        List<ClusteredLocation> res = getClusteredLocationsFromCache();
        Iterator<ClusteredLocation> it = res.iterator();
        while(it.hasNext()){
            ClusteredLocation cloc = it.next();
            if(!ids.contains(cloc.getId())){
                it.remove();
            }
        }
    return res;
    }

    public static List<ClusteredLocation> getCloseByClusteredLocationsFromCache(double distanceInMeter) {
        List<ClusteredLocation> all = getClusteredLocationsFromCache();
        Iterator<ClusteredLocation> it = all.iterator();
        while (it.hasNext()) {
            ClusteredLocation cl = it.next();
            double[] currentLoc = PTNMELocationManager.getLocationLatLng(false);
            if (currentLoc != null) {
                double distance = PTNMELocationManager.computeDistance(cl.getLoc(), currentLoc[0], currentLoc[1]);
                if (distance > distanceInMeter || distance < distance4Clustering) {
                    it.remove();
                }
            }

        }
        return all;

    }

    public static ClusteredLocation getClosestClusteredLocationsFromCache() {
        List<ClusteredLocation> all = getClusteredLocationsFromCache();
        ClusteredLocation res = null;
        double[] locations = PTNMELocationManager.getLocationLatLng(false);
        if (locations != null) {
            double x = PTNMELocationManager.getLocationLatLng(false)[0];
            double y = PTNMELocationManager.getLocationLatLng(false)[1];
            double mindistance = Double.MAX_VALUE;
            Iterator<ClusteredLocation> it = all.iterator();
            while (it.hasNext()) {
                ClusteredLocation cl = it.next();
                double distance = PTNMELocationManager.computeDistance(cl.getLoc(), x, y);
                if (distance < mindistance) {
                    res = cl;
                    mindistance = distance;
                }
            }
            return res;
        } else {
            return null;
        }

    }

    public static void addNewClusteredLocations(Map<Location, Dataset> locs, ClusterMetaData.ClusterType type) {
        boolean wasOpen = Utilities.isDbOpen();

        for (Location loc : locs.keySet()) {
            ClusteredLocation toAdd = null;
            List<ClusteredLocation> oldClusteredLocs = getClusteredLocationsFromCache();
            for (ClusteredLocation oldLoc : oldClusteredLocs) {
                double distance = PTNMELocationManager.computeDistance(loc, oldLoc.getLoc());
                if (distance < (2*distance4Clustering)) {
                    toAdd = oldLoc;
                    break;
                }
            }
            if (toAdd == null) {
                Utilities.openDBConnection().saveClusterLocation(loc, locs.get(loc), type);
            } else {
                Log.d("PTEnabler", "Updating Clustered Location: ID " + toAdd.getId() + " Last Clustered: " + new Date(toAdd.getDate()).toLocaleString());
                int lat = (loc.lat + toAdd.getLoc().lat) / 2;
                int lon = (loc.lon + toAdd.getLoc().lon) / 2;
                Location upatedLL;
                if (toAdd.getLoc().place != null) {
                    upatedLL = new Location(LocationType.ADDRESS, toAdd.getLoc().id, lat, lon, toAdd.getLoc().place, toAdd.getLoc().name);
                } else {
                    upatedLL = new Location(LocationType.ADDRESS, lat, lon);
                }

                toAdd.setDate(new Date().getTime());
                toAdd.setLoc(upatedLL);
                toAdd.setCount(toAdd.getCount() + 1);
                Utilities.openDBConnection().updateClusteredLocation(toAdd, locs.get(loc));

            }
        }


        if (!wasOpen) {
            Utilities.closeDBConnection();
        }
    }

    public static void addNewClusteredLocations(List<ClusteredLocation> clocs) {

        for (ClusteredLocation loc : clocs) {

            Utilities.openDBConnection().updateClusteredLocation(loc, null);

        }
    }

    public static void clearClusters() {
        clearClusters(14);
    }

    public static void clearClusters(int olderThanXdays) {
        Utilities.openDBConnection().clearClusteredLocations(olderThanXdays);
    }

    public static Set<PropableNextLocationResult> getProbableDestinations(long cid, boolean includeCurrent) {
        List<ClusteredLocation> clocations = getClusteredLocationsFromCache();
        if (probresult == null) {
            HashMap<Integer, ClusteredLocation> probLoc = new HashMap<Integer, ClusteredLocation>();
            TreeSet<UserLocation> ulocs = new TreeSet<UserLocation>();
            ulocs.addAll(Utilities.openDBConnection().getAllHistoryLocs(0, new Date().getTime(), false));

            // All IDs of currently available cluster
            Vector<Long> cids = new Vector<Long>();
            for (ClusteredLocation cl : clocations) {
                cids.add(cl.getId());
            }
            HashMap<Long, HashMap<Long, Integer>> sums = new HashMap<Long, HashMap<Long, Integer>>();
            Iterator<UserLocation> it = ulocs.iterator();
            long lastparentCluster = -1;
            int tempCount = 0;
            while (it.hasNext()) {
                UserLocation current = it.next();
                if (cids.contains(current.getParentCluster())) {
                    if (lastparentCluster == current.getParentCluster() || current.getParentCluster() == 0) {
                        // Iteration durch einen Cluster oder durch noise

                        //tempCount auf 0, um sicherzustellen, dass das vorbeilaufen an einem Cluster nicht gezählt wird.
                        tempCount = 0;
                        continue;
                    } else {
                        // Eintritt in neuen Cluster der von altem Abweicht
                        if (lastparentCluster == -1) {
                            // Eintritt in ersten Cluster
                            // Setzen und weiter
                            lastparentCluster = current.getParentCluster();
                            continue;
                        }


                        if (tempCount < 5) {
                        // Vorbeilaufen an Clustern ausschließen
                        // Vergleichswert heißt, dass 5 Locations im selben Cluster gefunden werden müssen mit jeweils 30 Sekunden differenz
                            tempCount++;
                            continue;
                        }
                        // Clusterwechsel (am Ende von Noise oder direkt)
                        HashMap<Long, Integer> c1Scores = sums.get(lastparentCluster);
                        if (c1Scores != null) {
                            Integer c1c2score = c1Scores.get(current.getParentCluster());
                            if (c1c2score == null) {
                                c1Scores.put(current.getParentCluster(), 1);
                            } else {
                                c1Scores.put(current.getParentCluster(), (c1c2score.intValue() + 1));
                            }
                        } else {
                            c1Scores = new HashMap<Long, Integer>();
                            c1Scores.put(current.getParentCluster(), 1);
                            sums.put(lastparentCluster, c1Scores);
                        }

                        tempCount = 0;
                        lastparentCluster = current.getParentCluster();
                    }
                }
            }
            probresult = calculateProbabilityForDestinations(sums);

        }
        HashMap<Long, Double> currentCluster = probresult.get(cid);
        if (currentCluster != null && currentCluster.size() != 0) {
            TreeSet<PropableNextLocationResult> results = new TreeSet<PropableNextLocationResult>();
            for (ClusteredLocation cloc : clocations) {
                if (currentCluster.keySet().contains(cloc.getId())) {
                    Location currentGeoPos = PTNMELocationManager.getLocation(false);
                    if ((currentGeoPos != null && PTNMELocationManager.computeDistance(cloc.getLoc(), currentGeoPos) > distance4Clustering) || includeCurrent)
                        results.add(new PropableNextLocationResult(currentCluster.get(cloc.getId()), cloc));
                }
            }
            return results;
        }

        return null;

    }

    public static Set<PropableNextLocationResult> getProbableDestinations(long cid) {
        return getProbableDestinations(cid, false);
    }

    public static Set<PropableNextLocationResult> getPropableLocationForDate(Date x, boolean includeCurPosCluster) {
        List<UserLocation> all = Utilities.openDBConnection().getAllHistoryLocs(0, new Date().getTime());
        Calendar toLookFor = Calendar.getInstance();
        toLookFor.setTimeInMillis(x.getTime());
        Calendar loccal = Calendar.getInstance();
        HashMap<Long, Double> props = new HashMap<Long, Double>();
        HashMap<Long, Integer> hourOfDaytoUse = new HashMap<Long, Integer>();
        for (UserLocation loc : all) {
            loccal.setTimeInMillis(loc.getDate());
            if (loc.getParentCluster() != 0
                    && loccal.get(Calendar.HOUR_OF_DAY) == toLookFor.get(Calendar.HOUR_OF_DAY)) {


                if (hourOfDaytoUse.get(loc.getParentCluster()) != null) {
                    if (loccal.get(Calendar.DAY_OF_WEEK) == toLookFor.get(Calendar.DAY_OF_WEEK)) {
                        hourOfDaytoUse.put(loc.getParentCluster(), hourOfDaytoUse.get(loc.getParentCluster()) + 4);
                    } else {
                        hourOfDaytoUse.put(loc.getParentCluster(), hourOfDaytoUse.get(loc.getParentCluster()) + 1);
                    }

                } else {
                    if (loccal.get(Calendar.DAY_OF_WEEK) == toLookFor.get(Calendar.DAY_OF_WEEK)) {
                        hourOfDaytoUse.put(loc.getParentCluster(), 4);
                    } else {
                        hourOfDaytoUse.put(loc.getParentCluster(), 1);
                    }

                }


            }
        }
        double sum = 0;
        for (int count : hourOfDaytoUse.values()) {
            sum += count;
        }

        for (long cid : hourOfDaytoUse.keySet()) {
            props.put(cid, ((double) hourOfDaytoUse.get(cid)) / sum);
        }
        TreeSet<PropableNextLocationResult> res = new TreeSet<PropableNextLocationResult>();
        for (ClusteredLocation cloc : getClusteredLocationsFromCache()) {
            if (props.keySet().contains(cloc.getId())) {
                double[] currentLoc = PTNMELocationManager.getLocationLatLng(false);
                double dist = Double.MAX_VALUE;
                if (currentLoc != null) {
                    dist = PTNMELocationManager.computeDistance(cloc.getLoc(), currentLoc[0], currentLoc[1]);
                }

                if (dist > distance4Clustering || includeCurPosCluster) {
                    res.add(new PropableNextLocationResult(props.get(cloc.getId()), cloc));
                }
            }
        }

        return res;
    }

    private static HashMap<Long, HashMap<Long, Double>> calculateProbabilityForDestinations(HashMap<Long, HashMap<Long, Integer>> inputs) {
        HashMap<Long, HashMap<Long, Double>> res = new HashMap<Long, HashMap<Long, Double>>();
        for (long c1 : inputs.keySet()) {
            HashMap<Long, Integer> c1scores = inputs.get(c1);
            double sum = 0;
            for (int count : c1scores.values()) {
                sum += count;
            }
            for (long c1c2 : c1scores.keySet()) {
                HashMap<Long, Double> c1c2prob = res.get(c1);
                if (c1c2prob == null) c1c2prob = new HashMap<Long, Double>();
                if (sum != 0) c1c2prob.put(c1c2, ((double) c1scores.get(c1c2)) / sum);
                res.put(c1, c1c2prob);
            }
        }

        return res;


    }
    public static String exportClusters(){

        List<ClusteredLocation> clusters = getClusteredLocationsFromCache();
        Gson gson = new Gson();
        String test = gson.toJson(clusters);
        return test;

    }

}