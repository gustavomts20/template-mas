package artifacts;

import cartago.*;
import cartago.tools.GUIArtifact;

import javax.swing.*;
import jason.asSyntax.Atom;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DroneArena extends GUIArtifact {

    private static final int SIZE = 10;
    private static final int CELL_SIZE = 50;
    private static final int MAX_BAT = 100;
    private static final int MOVE_COST = 1;
    private static final int LOW_BAT = 20;

    private final Map<String,Point> drones = new ConcurrentHashMap<>();
    private final Map<String,Integer> battery = new ConcurrentHashMap<>();
    private final Set<Point> obstacles = Collections.synchronizedSet(new HashSet<>());
    private final Set<Point> threats = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> recharging = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private JFrame frame;
    private DrawPanel drawPanel;
    private final Random rnd = new Random();

    public void init(){
        for(int i=0;i<10;i++){
            Point p;
            do{p=randomCell();}while(p.equals(new Point(0,0)) || obstacles.contains(p));
            obstacles.add(p);
        }
        defineObsProperty("threatsLeft",threats.size());
        createFrame();
        log("DroneArena ready.");
    }

    @OPERATION void registerDrone(String name){
        drones.put(name,new Point(0,0));
        battery.put(name,MAX_BAT);
        update();
    }

    @OPERATION void navigate(String name,int tx,int ty, OpFeedbackParam<Boolean> arrived){
        Point start = drones.get(name);
        List<Point> path = findPath(start,new Point(tx,ty));
        if(path==null){ arrived.set(false); return; }
        for(Point p:path){
            if(!alive()) return;
            drones.put(name,p);
            spendBattery(name,MOVE_COST);
            update();
            if(p.equals(new Point(0,0))){
                startRecharge(name);
            }
            nap(80);
        }
        arrived.set(true);
    }

    @OPERATION void scan(String name, OpFeedbackParam<Boolean> hit){
        Point cur = drones.get(name);
        boolean found = threats.remove(cur);
        if(found){
            getObsProperty("threatsLeft").updateValue(threats.size());
            signal("threatNeutralised",cur.x,cur.y);
        }
        hit.set(found);
        update();
    }

    @OPERATION void batteryLevel(String name, OpFeedbackParam<Integer> lvl){
        Integer b = battery.get(name);
        if(b==null){ failed("unknown_drone"); }
        else lvl.set(b);
    }

    @OPERATION void position(String name, OpFeedbackParam<Integer> x, OpFeedbackParam<Integer> y){
        Point p = drones.get(name);
        if(p==null){ failed("unknown_drone"); }
        else { x.set(p.x); y.set(p.y); }
    }

    @OPERATION void nearestThreat(int x0,int y0, OpFeedbackParam<Integer> tx, OpFeedbackParam<Integer> ty){
        if(threats.isEmpty()){ failed("no_threat"); return; }
        Point best=null; double bestD=Double.MAX_VALUE;
        for(Point t: threats){
            double d=Math.hypot(t.x-x0,t.y-y0);
            if(d<bestD){bestD=d; best=t;}
        }
        tx.set(best.x); ty.set(best.y);
    }

    @OPERATION void nearestCharger(int x0,int y0, OpFeedbackParam<Integer> cx, OpFeedbackParam<Integer> cy){
        cx.set(0); cy.set(0);
    }

    @OPERATION void assignTargets(){
        System.out.println("[env] assigning targets for threats=" + threats);
        for(Point t: new HashSet<>(threats)){
            String best=null; double bestD=Double.MAX_VALUE; int bestBat=0;
            for(String d: drones.keySet()){
                int bat=battery.getOrDefault(d,0);
                Point p=drones.get(d);
                double dist=Math.hypot(p.x-t.x,p.y-t.y);
                if(bat>LOW_BAT && dist<bestD){
                    bestD=dist; best=d; bestBat=bat;
                }
            }
            if(best!=null){
                System.out.println("[env] -> target for " + best + " at " + t);
                signal("target", new Atom(best), t.x, t.y);
            }
        }
    }

    private void startRecharge(String name){
        if(recharging.contains(name)) return;
        recharging.add(name);
        startThread(() -> {
            nap(5000);
            Point p = drones.get(name);
            if(p!=null && p.equals(new Point(0,0))){
                battery.put(name,MAX_BAT);
                signal("charged", new Atom(name));
                update();
            }
            recharging.remove(name);
        },"recharge-"+name);
    }

    private void spendBattery(String name,int c){
        battery.compute(name,(k,v)->{
            int lvl=(v==null?MAX_BAT:v)-c;
            if(lvl<=LOW_BAT) signal("lowBattery", new Atom(k), lvl);
            return Math.max(lvl,0);
        });
    }

    private List<Point> findPath(Point s, Point g){
        if(s.equals(g)) return Collections.emptyList();
        int[] dx={1,-1,0,0};
        int[] dy={0,0,1,-1};
        Queue<Point> q=new ArrayDeque<>();
        Map<Point,Point> prev=new HashMap<>();
        Set<Point> vis=new HashSet<>();
        q.add(s); vis.add(s);
        while(!q.isEmpty()){
            Point cur=q.poll();
            for(int i=0;i<4;i++){
                int nx=cur.x+dx[i];
                int ny=cur.y+dy[i];
                Point nxt=new Point(nx,ny);
                if(nx<0||nx>=SIZE||ny<0||ny>=SIZE) continue;
                if(obstacles.contains(nxt)) continue;
                if(vis.contains(nxt)) continue;
                vis.add(nxt);
                prev.put(nxt,cur);
                if(nxt.equals(g)) return reconstruct(s,g,prev);
                q.add(nxt);
            }
        }
        return null;
    }

    private List<Point> reconstruct(Point s,Point g,Map<Point,Point> prev){
        LinkedList<Point> path=new LinkedList<>();
        Point cur=g;
        while(!cur.equals(s)){
            path.addFirst(cur);
            cur=prev.get(cur);
        }
        return path;
    }

    private Point randomCell(){
        int x=rnd.nextInt(SIZE);
        int y=rnd.nextInt(SIZE);
        return new Point(x,y);
    }

    @INTERNAL_OPERATION void mouseGrid(int gx,int gy){
        Point cell=new Point(gx,gy);
        if(obstacles.contains(cell)) return;
        threats.add(cell);
        getObsProperty("threatsLeft").updateValue(threats.size());
        signal("threatAppeared",gx,gy);
        update();
    }

    private void createFrame(){
        frame=new JFrame("Drone Arena");
        drawPanel=new DrawPanel();
        frame.add(drawPanel);
        linkMouseToInternalOp(drawPanel);
        linkWindowClosingEventToOp(frame,"closed");
        frame.pack();
        frame.setVisible(true);
    }

    private void linkMouseToInternalOp(JPanel panel){
        panel.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){
                int col=e.getX()/CELL_SIZE;
                int row=e.getY()/CELL_SIZE;
                execInternalOp("mouseGrid",col,row);
            }
        });
    }

    @INTERNAL_OPERATION void closed(WindowEvent ev){
        stopThreads();
        if(frame!=null) frame.dispose();
    }

    @OPERATION void update(){ if(drawPanel!=null) drawPanel.repaint(); }

    private class DrawPanel extends JPanel{
        DrawPanel(){ setPreferredSize(new Dimension(SIZE*CELL_SIZE,SIZE*CELL_SIZE)); }
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            paintGrid(g); paintObstacles(g); paintThreats(g); paintChargers(g); paintDrones(g);
        }
    }

    private void paintGrid(Graphics g){
        g.setColor(Color.LIGHT_GRAY);
        for(int x=0;x<SIZE;x++)
            for(int y=0;y<SIZE;y++)
                g.drawRect(x*CELL_SIZE,y*CELL_SIZE,CELL_SIZE,CELL_SIZE);
    }
    private void paintObstacles(Graphics g){
        g.setColor(Color.DARK_GRAY);
        for(Point o:obstacles)
            g.fillRect(o.x*CELL_SIZE,o.y*CELL_SIZE,CELL_SIZE,CELL_SIZE);
    }
    private void paintThreats(Graphics g){
        g.setColor(Color.RED);
        for(Point t:threats)
            g.fillOval(t.x*CELL_SIZE+10,t.y*CELL_SIZE+10,CELL_SIZE-20,CELL_SIZE-20);
    }
    private void paintChargers(Graphics g){
        g.setColor(Color.GREEN);
        g.fillOval(5,5,CELL_SIZE-10,CELL_SIZE-10);
    }
    private void paintDrones(Graphics g){
        for(String n:drones.keySet()){
            Point p=drones.get(n);
            int px=p.x*CELL_SIZE+5;
            int py=p.y*CELL_SIZE+5;
            g.setColor(Color.BLUE);
            g.fillRect(px,py,CELL_SIZE-10,CELL_SIZE-10);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(10f));
            g.drawString(n,px+3,py+12);
            Integer lvl=battery.getOrDefault(n,0);
            g.drawString(lvl+"%",px+3,py+24);
        }
    }

    private final List<Thread> runningThreads = new ArrayList<>();

    private void startThread(Runnable r, String name){
        Thread t = new Thread(r,name);
        t.start();
        runningThreads.add(t);
    }

    private void stopThreads(){
        runningThreads.forEach(Thread::interrupt);
        runningThreads.clear();
    }

    private boolean alive(){
        return !Thread.currentThread().isInterrupted();
    }

    private void nap(long ms){
        try{ Thread.sleep(ms); }catch(InterruptedException e){ Thread.currentThread().interrupt(); }
    }
}

