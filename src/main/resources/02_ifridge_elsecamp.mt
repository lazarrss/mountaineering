routeplan basecamp summit() {
    altitude height <> 0;

    radio(height);

    ifridge (height < 0) {
        shout("Below basecamp");
    }
    elsecamp ifridge (height == 0) {
        shout("At basecamp");
    }
    elsecamp {
        shout("Above basecamp");
    }

    descend;
}
