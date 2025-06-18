!start.

+!start
 <-  .print("Coordinator online.").

+ready[source(D)]
 <-  lookupArtifact("env", EnvID); 
     focus(EnvID);
     position(D,X0,Y0);
     !compute_nearest(X0,Y0,D).

+threatAppeared(X,Y)
 <-  +threat(X,Y);
     position("drone1",DX,DY);
     !compute_nearest(DX,DY,drone1).

+threatNeutralised(X,Y)
 <-  -threat(X,Y);
     position("drone1",DX,DY);
     !compute_nearest(DX,DY,drone1).

+!compute_nearest(X0,Y0,From)
 :  threatsLeft(N) & N > 0
 <- nearestThreat(X0,Y0,BX,BY);
    .send(From, tell, target(BX,BY)).

+!compute_nearest(_X0,_Y0,From)
 :  threatsLeft(0)
 <- .print("No threats to assign to ", From).