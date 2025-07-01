!start.

+!start
 <- makeArtifact("env","artifacts.DroneArena",[],EnvID);
    focus(EnvID);
    .print("Coordenador preparado.");
    !!assign.

+ready[source(D)]
 <- .print(D," pronto");
    !!assign.

+threatAppeared(X,Y)
 <- .print("Ameaça em (",X,",",Y,")");
    !!assign.

+threatNeutralised(X,Y)
 <- .print("Ameaça neutralizada (",X,",",Y,")");
    !!assign.

+charged(D)
 <- .print(D," carregado");
    !!assign.

+!assign
 <- assignTargets.
