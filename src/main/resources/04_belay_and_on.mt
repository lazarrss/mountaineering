routeplan basecamp summit() {
    altitude altitudeNow <> 0;

    belay (altitudeNow < 3) {
        shout("Climbing...");
        altitudeNow <> altitudeNow + 1;
    }

    on {
        shout("Checking rope...");
        altitudeNow <> altitudeNow - 1;
    } belay (altitudeNow > 0);

    descend;
}
