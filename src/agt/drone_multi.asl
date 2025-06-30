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
    !!update_status;
    .send(central,tell,ready).

+!connect_env(N)[error(action_failed)]
 <- .wait(500);
    !!connect_env(N).

+!update_status : my_name(N)
 <- position(N,X,Y);
    .abolish(position(_,_));
    +position(X,Y);
    batteryLevel(N,L);
    .abolish(battery(_));
    +battery(L).

+target(N,TX,TY) : my_name(N)
 <- .abolish(target(_,_));
    +target(TX,TY);
    !!move_step.

+!move_step : position(X,Y) & target(TX,TY) & X < TX & my_name(N)
 <- NX = X + 1;
    navigate(N,NX,Y,Arr);
    !after_move(Arr).

+!move_step : position(X,Y) & target(TX,TY) & X > TX & my_name(N)
 <- NX = X - 1;
    navigate(N,NX,Y,Arr);
    !after_move(Arr).

+!move_step : position(X,Y) & target(TX,TY) & (X == TX) & Y < TY & my_name(N)
 <- NY = Y + 1;
    navigate(N,X,NY,Arr);
    !after_move(Arr).

+!move_step : position(X,Y) & target(TX,TY) & (X == TX) & Y > TY & my_name(N)
 <- NY = Y - 1;
    navigate(N,X,NY,Arr);
    !after_move(Arr).

+!move_step : position(X,Y) & target(X,Y)
 <- .print("Arrived at target (",X,",",Y,")");
    !!scan.

+!after_move(true)
 <- !!update_status;
    !!move_step.

+!after_move(false)
 <- .print("Step failed, retrying …");
    !!update_status;
    !!move_step.

+lowBattery(N,Lvl) : my_name(N)
 <- .print("Low battery ",Lvl,"%, heading to charger.");
    .abolish(target(_,_));
    +target(0,0);
    !!move_step.

+charged(N) : my_name(N)
 <- .print("Drone ",N," recharged.").

+!scan : my_name(N)
 <- scan(N,Hit);
    .print("Scan result ",Hit).
