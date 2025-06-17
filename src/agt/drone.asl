{ include("$jacamo/templates/common-cartago.asl") }

!start.

+!start
 <- .my_name(Me);
    lookupArtifact("grid",Env) | makeArtifact("grid","artifacts.PatrolEnv",[500,200,700,500,10],Env);
    focus(Env);
    registerDrone(Me);
    .send(coord,tell,ready(Me));
    .print("Drone ",Me," ready.").

+gather(X,Y)[source(coord)]
 <- !goto(X,Y).

+threat(X,Y)[source(Env)]
 <- .send(coord,tell,threat(X,Y)).

+!goto(X,Y)
 <- .my_name(Me);
    moveAndScan(Me,X,Y,F);
    if F then .send(coord,tell,neutralized(X,Y));
    .print("scanned (",X,",",Y,") threat=",F).

