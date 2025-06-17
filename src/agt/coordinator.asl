{ include("$jacamo/templates/common-cartago.asl") }

!start.

+!start
 <- makeArtifact("grid","artifacts.PatrolEnv",[500,200,700,500,10],Env);
    focus(Env);
    startPatrol.
    .print("Coordinator ready.").

+ready(D)[source(D)]
 <- +drone(D).

+threat(X,Y)
 <- .broadcast(tell,gather(X,Y)).

+neutralized(X,Y)[source(D)]
 <- .print("Threat at (",X,",",Y,") neutralized by ",D).

