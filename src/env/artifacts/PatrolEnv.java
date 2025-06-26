package artifacts;

import cartago.*;
import cartago.tools.GUIArtifact;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class PatrolEnv extends GUIArtifact {

    private final Map<String,Integer> battery = new ConcurrentHashMap<>();
    private static final int MAX_BAT = 100, MOVE_COST = 1, SCAN_COST = 2,
                            LOW_BAT = 20;

    private final Random rnd = new Random();

    private int x1, y1, x2, y2, stepX, stepY;
    private static final int CELL_SIZE = 40;

    private final Set<Point> threats   = Collections.synchronizedSet(new HashSet<>());
    private final Set<Point> obstacles = Collections.synchronizedSet(new HashSet<>());
    private final Map<Point, Integer>            scanCounts = new ConcurrentHashMap<>();
    private final Map<String, Point>             drones     = new ConcurrentHashMap<>();
    private final List<Point>                    chargers   = new ArrayList<>();

    private JFrame frame;
    private DrawPanel drawPanel;
    private MetricsPanel metricsPanel;

    public void init(int x1, int y1, int x2, int y2, int nThreats) {
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        stepX = Math.max(1, (x2 - x1) / 10);
        stepY = Math.max(1, (y2 - y1) / 10);


        for (int i = 0; i < 5; i++) obstacles.add(randomCell());
        chargers.add(new Point(x1, y1));
        chargers.add(new Point(x2, y2));

        defineObsProperty("threatsLeft", threats.size());
        defineObsProperty("gridStep", stepX, stepY);

        createFrame();

        log("PatrolEnv ready.");
    }

    private void announceThreat(Point p) {
        signal("threatAppeared", p.x, p.y);
    }

    @OPERATION void registerDrone(String name){
        drones.put(name,new Point(x1,y1));
        battery.put(name,MAX_BAT);
        update();
    }

    @OPERATION void navigate(String name, int tx, int ty,
                             OpFeedbackParam<Boolean> arrived) {

        Point start = drones.get(name);
        List<Point> path = findPath(start, new Point(tx, ty));
        if (path == null) { arrived.set(false); return; }

        for (Point p : path) {
            if (!alive()) return;
            drones.put(name, p);
            spendBattery(name, MOVE_COST);
            update();

            /* New: auto-recharge */
            if (isCharger(p) && battery.get(name) < MAX_BAT) {
                battery.put(name, MAX_BAT);
                signal("charged", name);
            }
            nap(80);
        }
        arrived.set(true);
    }

    private List<Point> findPath(Point s, Point g) {

        if (s.equals(g)) {
            return Collections.emptyList();
        }

        int[] dx = {  stepX, -stepX,      0,      0 };
        int[] dy = {      0,      0,  stepY, -stepY };

        Queue<Point> frontier      = new ArrayDeque<>();
        Map<Point, Point> cameFrom = new HashMap<>();
        Set<Point> visited         = new HashSet<>();

        frontier.add(s);
        visited.add(s);

        while (!frontier.isEmpty()) {
            Point cur = frontier.poll();

            for (int i = 0; i < 4; i++) {
                int nx = cur.x + dx[i];
                int ny = cur.y + dy[i];
                Point nxt = new Point(nx, ny);

                if (nx < x1 || nx > x2 || ny < y1 || ny > y2)   continue;
                if (obstacles.contains(nxt))                    continue;
                if (visited.contains(nxt))                      continue;

                visited.add(nxt);
                cameFrom.put(nxt, cur);

                if (nxt.equals(g)) {
                    return reconstructPath(s, g, cameFrom);
                }
                frontier.add(nxt);
            }
        }
        return null;
    }

    private List<Point> reconstructPath(Point s, Point g,
                                        Map<Point, Point> cameFrom) {
        LinkedList<Point> path = new LinkedList<>();
        Point cur = g;
        while (!cur.equals(s)) {
            path.addFirst(cur);
            cur = cameFrom.get(cur);
        }
        return path;
    }   

    private void spendBattery(String n,int c){
        battery.compute(n,(k,v)->{
            int lvl=(v==null?MAX_BAT:v)-c;
            if(lvl<=LOW_BAT) signal("lowBattery",k,lvl);
            return Math.max(lvl,0);
        });
    }

    @OPERATION void nearestCharger(int x,int y,
                                OpFeedbackParam<Integer> cx,
                                OpFeedbackParam<Integer> cy){
        Point best=null; double bestD=Double.MAX_VALUE;
        for(Point c:chargers){
            double d=Math.hypot(c.x-x,c.y-y);
            if(d<bestD){bestD=d; best=c;}
        }
        cx.set(best.x); cy.set(best.y);
    }

    @OPERATION
    void my_dist(int x1, int y1, int x2, int y2, OpFeedbackParam<Double> dist) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double result = Math.sqrt(dx * dx + dy * dy);
        dist.set(result);
    }

    @OPERATION void position(String name,
                            OpFeedbackParam<Integer> x,
                            OpFeedbackParam<Integer> y)
    {
        Point p = drones.get(name);
        if (p == null) {
            failed("unknown_drone");
        } else {
            x.set(p.x);
            y.set(p.y);
        }
    }

    @INTERNAL_OPERATION void spawnThreat() {
        Point c = randomCell();
        threats.add(c);
        getObsProperty("threatsLeft").updateValue(threats.size());
        announceThreat(c);
        update();
    }

    @OPERATION void move(String name, int x, int y, OpFeedbackParam<Boolean> ok) {
        Point target = new Point(x, y);

        if (x < x1 || x > x2 || y < y1 || y > y2) {
            ok.set(false);
            return;
        }

        if (obstacles.contains(target)) {
            ok.set(false);
            return;
        }

        drones.put(name, target);
        ok.set(true);
        update();
    }

    @OPERATION void scan(String name, int x, int y, OpFeedbackParam<Boolean> hit) {
        Point c = new Point(x, y);
        scanCounts.merge(c, 1, Integer::sum);

        boolean real     = threats.contains(c);
        boolean sensed   = real;

        if (sensed && real) {
            threats.remove(c);
            getObsProperty("threatsLeft").updateValue(threats.size());
            signal("threatNeutralised", x, y);
        }
        hit.set(sensed);
        metricsPanel.record(name, sensed);
        spendBattery(name, SCAN_COST);
        update();
    }

    @OPERATION void recharge(String n, OpFeedbackParam<Boolean> ok){
        Point here=drones.get(n);
        boolean at = chargers.contains(here);
        if(at){
            battery.put(n,MAX_BAT);
            signal("charged",n);
        }
        ok.set(at);
    }

    @OPERATION void batteryLevel(String n, OpFeedbackParam<Integer> lvl){
        Integer v = battery.get(n);
        if(v==null){ failed("unknown_drone"); }
        else        { lvl.set(v); }
    }

    @OPERATION void update() {
        if (drawPanel    != null) drawPanel.repaint();
        if (metricsPanel != null) metricsPanel.repaint();
    }

    @OPERATION void nearestThreat(int x0, int y0,
                                OpFeedbackParam<Integer> tx,
                                OpFeedbackParam<Integer> ty) {
        if (threats.isEmpty()) {
            failed("no_threat");
            return;
        }
        Point best = null;
        double bestD = Double.MAX_VALUE;

        for (Point t : threats) {
            double d = Math.hypot(t.x - x0, t.y - y0);
            if (d < bestD) {
                best  = t;
                bestD = d;
            }
        }
        tx.set(best.x);
        ty.set(best.y);
    }

    @INTERNAL_OPERATION void patrolStepAsc(int cx, int cy)  { signal("cellScannedAsc",  cx, cy); }
    @INTERNAL_OPERATION void patrolStepDesc(int cx, int cy) { signal("cellScannedDesc", cx, cy); }

    @INTERNAL_OPERATION void closed(WindowEvent ev) {
        log("GUI closed, stopping environment.");
        stopThreads();
        if (frame != null) frame.dispose();
    }

    @INTERNAL_OPERATION void mouseGrid(int gx, int gy) {
        Point cell = new Point(gx, gy);
        threats.add(cell);
        getObsProperty("threatsLeft").updateValue(threats.size());
        announceThreat(cell);
        update();
    }

    private final List<Thread> runningThreads = new ArrayList<>();

    private void createFrame() {
        frame = new JFrame("Patrol Environment");
        frame.setLayout(new BorderLayout());

        drawPanel    = new DrawPanel();
        metricsPanel = new MetricsPanel();

        frame.add(drawPanel,    BorderLayout.CENTER);
        frame.add(metricsPanel, BorderLayout.EAST);

        linkMouseToInternalOp(drawPanel);

        linkWindowClosingEventToOp(frame, "closed");

        frame.pack();
        frame.setVisible(true);
    }

    private void linkMouseToInternalOp(JPanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                Point cell = pixelToCell(e.getX(), e.getY());
                execInternalOp("mouseGrid", cell.x, cell.y);
            }
        });
    }

    private class DrawPanel extends JPanel {
        DrawPanel() {
            int w = ((x2 - x1) / stepX + 1) * CELL_SIZE;
            int h = ((y2 - y1) / stepY + 1) * CELL_SIZE;
            setPreferredSize(new Dimension(w, h));
        }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintGrid(g);
            paintObstacles(g);
            paintThreats(g);
            paintChargers(g);
            paintDrones(g);
        }
    }

    private class MetricsPanel extends JPanel {
        private final Map<String,Integer> scans       = new ConcurrentHashMap<>();
        private final Map<String,Integer> neutralised = new ConcurrentHashMap<>();

        MetricsPanel() { setPreferredSize(new Dimension(220, 0)); }

        void record(String name, boolean hit) {
            scans.merge(name, 1, Integer::sum);
            if (hit) neutralised.merge(name, 1, Integer::sum);
            repaint();
        }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.BLACK);
            int y = 20;
            for (String d : drones.keySet()) {
                int s = scans.getOrDefault(d, 0);
                int n = neutralised.getOrDefault(d, 0);
                g.drawString(d + ": scans=" + s + "  hits=" + n, 10, y);
                y += 20;
            }
        }
    }

    private void paintGrid(Graphics g) {
        for (int y = y1; y <= y2; y += stepY)
            for (int x = x1; x <= x2; x += stepX) {
                int px = (x - x1) / stepX * CELL_SIZE;
                int py = (y - y1) / stepY * CELL_SIZE;
                g.setColor(Color.LIGHT_GRAY);
                g.drawRect(px, py, CELL_SIZE, CELL_SIZE);

                Integer cnt = scanCounts.get(new Point(x, y));
                if (cnt != null) {
                    int alpha = Math.min(255, cnt * 10);
                    g.setColor(new Color(255, 255, 0, alpha));
                    g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
                }
            }
    }
    private void paintObstacles(Graphics g) {
        g.setColor(Color.DARK_GRAY);
        for (Point o : obstacles) paintCellFill(g, o, CELL_SIZE, CELL_SIZE);
    }
    private void paintThreats(Graphics g) {
        g.setColor(Color.RED);
        Set<Point> snap;
        synchronized (threats) {
            snap = new HashSet<>(threats);
        }
        for (Point t : snap) {
            int px = (t.x - x1) / stepX * CELL_SIZE;
            int py = (t.y - y1) / stepY * CELL_SIZE;
            g.fillOval(px + 10, py + 10, CELL_SIZE - 20, CELL_SIZE - 20);
        }
    }
    private void paintChargers(Graphics g) {
        g.setColor(Color.GREEN);
        for (Point c : chargers)
            paintCellOval(g, c, CELL_SIZE - 10, CELL_SIZE - 10, 5, 5);
    }
    
    private void paintDrones(Graphics g) {
        for (Map.Entry<String, Point> e : drones.entrySet()) {
            String name = e.getKey();
            Point  p    = e.getValue();

            int px = (p.x - x1) / stepX * CELL_SIZE + 5;
            int py = (p.y - y1) / stepY * CELL_SIZE + 5;

            g.setColor(Color.BLUE);
            g.fillRect(px, py, CELL_SIZE - 10, CELL_SIZE - 10);

            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));

            g.drawString(name, px + 3, py + 12);

            Integer lvl = battery.getOrDefault(name, 0);
            g.drawString(lvl + "%", px + 3, py + 24);
        }
    }

    private void paintCellFill(Graphics g, Point c, int w, int h) {
        int px = (c.x - x1) / stepX * CELL_SIZE;
        int py = (c.y - y1) / stepY * CELL_SIZE;
        g.fillRect(px, py, w, h);
    }
    private void paintCellOval(Graphics g, Point c, int w, int h, int dx, int dy) {
        int px = (c.x - x1) / stepX * CELL_SIZE + dx;
        int py = (c.y - y1) / stepY * CELL_SIZE + dy;
        g.fillOval(px, py, w, h);
    }

    private Point randomCell() {
        int gx = x1 + rnd.nextInt((x2 - x1) / stepX + 1) * stepX;
        int gy = y1 + rnd.nextInt((y2 - y1) / stepY + 1) * stepY;
        return new Point(gx, gy);
    }
    private Point pixelToCell(int px, int py) {
        int col = px / CELL_SIZE;
        int row = py / CELL_SIZE;
        return new Point(x1 + col * stepX, y1 + row * stepY);
    }
    private void startThread(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.start();
        runningThreads.add(t);
    }
    private void stopThreads() {
        runningThreads.forEach(Thread::interrupt);
        runningThreads.clear();
    }
    private boolean alive() { return !Thread.currentThread().isInterrupted(); }
    private void nap(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private boolean isCharger(Point p) {
        return chargers.contains(p);
    }
}