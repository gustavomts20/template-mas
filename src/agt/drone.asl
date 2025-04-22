{ include("$jacamo/templates/common-cartago.asl") }

!inicializar_drone.

+!inicializar_drone
 <- makeArtifact("grid","artifacts.PatrolEnv",[500,200,700,500,10],G);
    focus(G);
    .print("Drone inicializado: workspace=",G);
    startPatrol.

+cellScanned(X,Y)
 <- .print("→ Patrulhando Região (",X,",",Y,")").

+threat(X,Y)
 <- .print("Possivel ameaça detectada na região (",X,",",Y,")").

+complete
 <- .print("Patrulha completa!").