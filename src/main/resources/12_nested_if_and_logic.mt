routeplan basecamp summit() {
    altitude temp <> 15;
    secure cold;
    secure freezing;

    cold <> temp < 10;
    freezing <> temp < 0;

    ifridge (freezing or (cold and not freezing)) {
        ifridge (temp < -5) {
            shout("Extreme cold");
        }
        elsecamp {
            shout("Cold weather");
        }
    }
    elsecamp {
        shout("Normal weather");
    }

    descend;
}
