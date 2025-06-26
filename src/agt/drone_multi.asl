my_name(Name).

!start.

+!start
 <- lookupArtifact("env",EnvID);
    focus(EnvID);
    ?my_name(N);
    registerDrone(N);
    .print("Drone ",N," ready.");
    .send(central, tell, ready).

+target(N,X,Y)[source(env)] : my_name(N)
 <- .print("Moving to target (",X,",",Y,")");
    navigate(N,X,Y,Arr);
    if Arr then !!scan.

+lowBattery(N,Lvl)[source(env)] : my_name(N)
 <- .print("Low battery ",Lvl,", heading to charger.");
    nearestCharger(N,CX,CY);
    navigate(N,CX,CY,_).

+charged(N)[source(env)] : my_name(N)
 <- .print("Drone ",N," recharged.");

+!scan
 <- ?my_name(N); scan(N,Hit);
    .print("Scan result ",Hit).
