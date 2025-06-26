!start.

+!start
 <- makeArtifact("env","artifacts.DroneArena",[],EnvID);
    focus(EnvID);
    .print("Coordinator ready.");
    !!assign.

+ready[source(D)]
 <- .print(D," ready");
    !!assign.

+threatAppeared(X,Y)
 <- .print("Threat at (",X,",",Y,")");
    !!assign.

+threatNeutralised(X,Y)
 <- .print("Threat neutralised (",X,",",Y,")");
    !!assign.

+charged(D)
 <- .print(D," charged");
    !!assign.

+!assign
 <- assignTargets.
