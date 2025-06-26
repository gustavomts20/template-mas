my_name(drone1).

!init_patrol.

+!init_patrol
 <-
     makeArtifact("env","artifacts.PatrolEnv",[0,0,400,400,15],EnvID);
     focus(EnvID);
     ?my_name(N);
     registerDrone(N);
     .print("Drone ",N," ready for patrol.");
     .send(coordinator, tell, ready);
     !!await_cells.

+target(X,Y)[source(coordinator)]
 <-  .print("Target received: (",X,",",Y,")");
     !!scan_cell(X,Y).

+lowBattery(N,Lvl)
<-  .print("Low battery (",Lvl,") – heading to charger.");
    position(N,X0,Y0);
    nearestCharger(X0,Y0,CX,CY);
    simpleNavigate(N,CX,CY,_).

+!scan_cell(X,Y)
 <- ?my_name(N);
     simpleNavigate(N,X,Y,Arrived);
    !handle_arrival(N,X,Y,Arrived).

+!handle_arrival(N,X,Y,true)
 <- scan(N,X,Y,Hit).

+!handle_arrival(N,X,Y,false)
 <- !!scan_cell(X,Y).

+charged(N)
 <- .print("Drone ",N," fully recharged.");
    !!await_cells.

+!await_cells
 <- .print("Awaiting cells to scan...").