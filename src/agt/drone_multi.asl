!start.

+!start
 <- .my_name(N);
    +my_name(N);
    !!connect_env(N).

+!connect_env(N)
 <- lookupArtifact("env",EnvID);
    focus(EnvID);
    registerDrone(N);
    .print("Drone ",N," ready.");
    .send(central, tell, ready).

+!connect_env(N)[error(action_failed)]
 <- .wait(500);
    !!connect_env(N).

+target(N,X,Y) : my_name(N)
 <- .print("Moving to target (",X,",",Y,")");
    navigate(N,X,Y,Arr);
    !after_nav(Arr).

+!after_nav(true)
 <- !!scan.

+!after_nav(false)
 <- true.

+lowBattery(N,Lvl) : my_name(N)
 <- .print("Low battery ",Lvl,", heading to charger.");
    navigate(N,0,0,_).

+charged(N) : my_name(N)
 <- .print("Drone ",N," recharged.").

+!scan
 <- ?my_name(N);
    scan(N,Hit);
    .print("Scan result ",Hit).
