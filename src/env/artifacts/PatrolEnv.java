package artifacts;

import cartago.*;
import cartago.tools.GUIArtifact;
import javax.swing.*;
import java.awt.*;
import java.util.*;

public class PatrolEnv extends GUIArtifact {

    private int  x1, y1, x2, y2;
    private int  stepX, stepY;
    private final Set<Point> threats = new HashSet<>();
    private final Random rnd = new Random();
    private final Map<String, Point> drones = new HashMap<>();

    private JFrame frame;
    private DrawPanel panel;
    private final int CELL_SIZE = 40;

    void init(int x1, int y1, int x2, int y2, int nThreats) {
        this.x1 = x1;  this.y1 = y1;
        this.x2 = x2;  this.y2 = y2;

        stepX = Math.max(1, (x2 - x1) / 5);
        stepY = Math.max(1, (y2 - y1) / 5);

        while (threats.size() < nThreats) {
            int gx = x1 + rnd.nextInt((x2 - x1) / stepX + 1) * stepX;
            int gy = y1 + rnd.nextInt((y2 - y1) / stepY + 1) * stepY;
            threats.add(new Point(gx, gy));
        }

        defineObsProperty("threatsLeft", threats.size());
        createGUI();
    }

    @OPERATION void scan(int x, int y, OpFeedbackParam<Boolean> found) {
        boolean hit = threats.remove(new Point(x, y));
        if (hit) {
            getObsProperty("threatsLeft").updateValue(threats.size());
        }
        found.set(hit);
    }

    @OPERATION void registerDrone(String name) {
        drones.put(name, new Point(x1, y1));
        updatePanel();
    }

    @OPERATION void moveAndScan(String name, int x, int y, OpFeedbackParam<Boolean> found) {
        drones.put(name, new Point(x, y));
        boolean hit = threats.remove(new Point(x, y));
        if (hit) {
            getObsProperty("threatsLeft").updateValue(threats.size());
            signal("threat", x, y);
        }
        found.set(hit);
        signal("droneMoved", name, x, y);
        updatePanel();
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

    private void createGUI() {
        panel = new DrawPanel();
        frame = new JFrame("Patrol Environment");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);
    }

    private void updatePanel() {
        if (panel != null) {
            panel.repaint();
        }
    }

    private class DrawPanel extends JPanel {
        DrawPanel() {
            int w = ((x2 - x1) / stepX + 1) * CELL_SIZE;
            int h = ((y2 - y1) / stepY + 1) * CELL_SIZE;
            setPreferredSize(new Dimension(w, h));
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int y = y1; y <= y2; y += stepY) {
                for (int x = x1; x <= x2; x += stepX) {
                    int px = (x - x1) / stepX * CELL_SIZE;
                    int py = (y - y1) / stepY * CELL_SIZE;
                    g.setColor(Color.LIGHT_GRAY);
                    g.drawRect(px, py, CELL_SIZE, CELL_SIZE);
                }
            }

            g.setColor(Color.RED);
            for (Point t : threats) {
                int px = (t.x - x1) / stepX * CELL_SIZE;
                int py = (t.y - y1) / stepY * CELL_SIZE;
                g.fillOval(px + 10, py + 10, CELL_SIZE - 20, CELL_SIZE - 20);
            }

            g.setColor(Color.BLUE);
            for (Map.Entry<String, Point> e : drones.entrySet()) {
                Point p = e.getValue();
                int px = (p.x - x1) / stepX * CELL_SIZE;
                int py = (p.y - y1) / stepY * CELL_SIZE;
                g.fillRect(px + 5, py + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                g.setColor(Color.WHITE);
                g.drawString(e.getKey(), px + 8, py + 20);
                g.setColor(Color.BLUE);
            }
        }
    }
}
