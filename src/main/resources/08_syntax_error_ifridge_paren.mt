routeplan basecamp summit() {
    altitude a <> 5;

    ifridge (a < 10 {
        shout("Too small");
    }
    elsecamp {
        shout("Big enough");
    }

    descend;
}
