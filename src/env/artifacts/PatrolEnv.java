package artifacts;

import cartago.*;
import java.awt.Point;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class PatrolEnv extends Artifact {

    private int  x1, y1, x2, y2;
    private final Set<Point> threats = new HashSet<>();
    private final Random rnd = new Random();

    void init(int x1, int y1, int x2, int y2, int nThreats) {
        this.x1 = x1;  this.y1 = y1;
        this.x2 = x2;  this.y2 = y2;

        int stepX = Math.max(1, (x2 - x1) / 5);
        int stepY = Math.max(1, (y2 - y1) / 5);

        while (threats.size() < nThreats) {
            int gx = x1 + rnd.nextInt((x2 - x1) / stepX + 1) * stepX;
            int gy = y1 + rnd.nextInt((y2 - y1) / stepY + 1) * stepY;
            threats.add(new Point(gx, gy));
        }

        defineObsProperty("threatsLeft", threats.size());
    }

    @OPERATION void scan(int x, int y, OpFeedbackParam<Boolean> found) {
        boolean hit = threats.remove(new Point(x, y));
        if (hit) {
            getObsProperty("threatsLeft").updateValue(threats.size());
        }
        found.set(hit);
    }

    @OPERATION void startPatrol() {
        new Thread(new PatrolSimulator()).start();
    }

    @INTERNAL_OPERATION void patrolStep(int cx, int cy) {
        signal("cellScanned", cx, cy);

        if (threats.remove(new Point(cx, cy))) {
            getObsProperty("threatsLeft").updateValue(threats.size());
            signal("threat", cx, cy);
        }
    }

    @INTERNAL_OPERATION void patrolDone() {
        signal("complete");
    }

    private class PatrolSimulator implements Runnable {
        public void run() {
            int stepX = Math.max(1, (x2 - x1) / 5);
            int stepY = Math.max(1, (y2 - y1) / 5);

            for (int y = y1; y <= y2; y += stepY) {
                for (int x = x1; x <= x2; x += stepX) {
                    execInternalOp("patrolStep", x, y);
                    try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                }
            }
            execInternalOp("patrolDone");
        }
    }
}