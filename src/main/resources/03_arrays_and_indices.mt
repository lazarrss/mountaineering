routeplan basecamp summit() {
    altitude i;
    altitude steps[5];

    climb (altitude i <> 0; i < 5; i <> i + 1) {
        steps[i] <> i * 10;
    }

    climb (altitude i <> 0; i < 5; i <> i + 1) {
        shout("Step");
        radio(steps[i]);
    }

    descend;
}
