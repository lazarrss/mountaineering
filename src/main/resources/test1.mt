routeplan altitude max2(altitude a, altitude b) {
    ifridge (a > b) {
        descend a;
    } elsecamp {
        descend b;
    }
}

routeplan altitude min2(altitude a, altitude b) {
    ifridge (a < b) {
        descend a;
    } elsecamp {
        descend b;
    }
}

routeplan altitude clamp(altitude x, altitude lo, altitude hi) {
    descend min2(max2(x, lo), hi);
}

routeplan slope avg3(slope a, slope b, slope c) {
    descend (a + b + c) / 3.0;
}

routeplan basecamp summit() {
    altitude a[8];

    a[0] <> 5;
    a[1] <> -2;
    a[2] <> 9;
    a[3] <> 12;
    a[4] <> -7;
    a[5] <> 3;
    a[6] <> 0;
    a[7] <> 4;

    altitude i <> 0;
    altitude sum <> 0;
    altitude best <> -999;

    belay (i < 8) {
        altitude v <> a[i];

        v <> clamp(v, -3, 10);

        sum <> sum + v;

        ifridge (v > best) {
            best <> v;
        } elsecamp {
        }

        i <> i + 1;
    }

    shout(sum);
    shout(best);

    slope s <> avg3(10.0, 5.0, 4.0);
    shout(s);

    slope whole <> 12.0;
    altitude ok <> (altitude) whole;
    shout(ok);

    descend;
}
