routeplan basecamp summit() {
    secure safe <> anchored;
    secure hasRope <> loose;
    secure okToClimb;

    okToClimb <> safe and not hasRope;

    ifridge (okToClimb) {
        shout("Climb started");
    }
    elsecamp {
        shout("Climb aborted");
    }

    descend;
}
