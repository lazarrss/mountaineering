routeplan altitude f(altitude a) { descend a; }

routeplan basecamp summit() {
    altitude x <> f();
    descend;
}
