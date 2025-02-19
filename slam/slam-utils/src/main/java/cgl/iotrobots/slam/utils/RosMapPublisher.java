package cgl.iotrobots.slam.utils;

import cgl.iotrobots.slam.core.app.GFSMap;
import geometry_msgs.Point;
import geometry_msgs.Pose;
import geometry_msgs.Quaternion;
import nav_msgs.MapMetaData;
import nav_msgs.OccupancyGrid;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.ros.concurrent.CancellableLoop;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RosMapPublisher extends AbstractNodeMain {
    private boolean gotMap = false;

//    private OccupancyGrid map;

    private String name = "slam_map";

    private BlockingQueue<GFSMap> maps = new ArrayBlockingQueue<GFSMap>(4);

    public RosMapPublisher() {
    }

    public void addMap(GFSMap map) {
        try {
            maps.put(map);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    int counter = 0;

    private void populateRosMAp(OccupancyGrid map, GFSMap smap) {
        map.getHeader().setFrameId("map");
        map.getHeader().setSeq(counter);
        map.getHeader().setStamp(new Time(System.currentTimeMillis() / 1000));

        MapMetaData metaData = map.getInfo();

        int size = 0;
        if (smap.getHeight() > smap.getWidth()) {
            size = smap.getHeight();
        } else {
            size = smap.getWidth();
        }

        metaData.setHeight(size);
        metaData.setWidth(size);
        // create a pose
        Pose p = map.getInfo().getOrigin();
        Quaternion q = p.getOrientation();

        Point point = p.getPosition();
        point.setX(smap.getOrigin().getX());
        point.setY(smap.getOrigin().getY());
        point.setZ(smap.getOrigin().getZ());

        metaData.setMapLoadTime(new Time(1));

//        if (!gotMap) {
            metaData.setResolution((float) smap.getResolution());

            q.setX(0);
            q.setY(0);
            q.setZ(0);
            q.setW(1);

            point.setX(0);
            point.setY(0);
            point.setZ(0);
//        }


        ChannelBuffer b = ChannelBuffers.buffer(ByteOrder.LITTLE_ENDIAN, size * size);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (x < smap.getWidth() && y < smap.getHeight()) {
                    b.writeByte(smap.getData()[MAP_IDX(smap.getWidth(), x, y)]);
                } else {
                    b.writeByte(-1);
                }
            }
        }

        map.setData(b);

//        gotMap = true;
    }

    public static int MAP_IDX(int sx, int i, int j) {
        return ((sx) * (j) + (i));
    }

//    public OccupancyGrid getMap() {
//        return map;
//    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("/" + name);
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        super.onStart(connectedNode);

        final Publisher<OccupancyGrid> mapPublisher = connectedNode.newPublisher("/map", OccupancyGrid._TYPE);
        final Publisher<MapMetaData> metaDataPublisher = connectedNode.newPublisher("/map_metadata", MapMetaData._TYPE);

//        this.map = mapPublisher.newMessage();
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            @Override
            protected void loop() throws InterruptedException {
                GFSMap gfsMap = maps.take();

                if (gfsMap != null) {
                    OccupancyGrid map = mapPublisher.newMessage();
                    populateRosMAp(map, gfsMap);
                    metaDataPublisher.publish(map.getInfo());
                    mapPublisher.publish(map);
                }
            }
        });
    }
}
