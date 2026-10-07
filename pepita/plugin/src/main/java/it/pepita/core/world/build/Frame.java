package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;

/**
 * Sistema di riferimento locale per costruire facciate/strutture orientate.
 * u = lungo la facciata (verso destra per chi la guarda da fuori), v = verso l'esterno, y = in alto.
 * Nelle stringhe dei blocchi i segnaposto {out} {in} {left} {right} diventano le direzioni cardinali vere.
 */
public final class Frame {
    public final BuildPlan p;
    public final int ox, oy, oz;
    public final String out, in, right, left;
    final int ux, uz, vx, vz;

    /** @param outward direzione verso cui guarda la facciata (north/south/east/west) */
    public Frame(BuildPlan p, int ox, int oy, int oz, String outward) {
        this.p = p;
        this.ox = ox; this.oy = oy; this.oz = oz;
        this.out = outward;
        this.in = BuildUtil.opp(outward);
        this.right = BuildUtil.ccw(outward);
        this.left = BuildUtil.opp(right);
        int[] v = BuildUtil.vec(outward), u = BuildUtil.vec(right);
        ux = u[0]; uz = u[1]; vx = v[0]; vz = v[1];
    }

    public int wx(int u, int v) { return ox + u * ux + v * vx; }

    public int wz(int u, int v) { return oz + u * uz + v * vz; }

    public String r(String b) {
        if (b.indexOf('{') < 0) return b;
        return b.replace("{out}", out).replace("{in}", in).replace("{right}", right).replace("{left}", left)
                .replace("{axisu}", ux != 0 ? "x" : "z").replace("{axisv}", vx != 0 ? "x" : "z");
    }

    public void set(int u, int y, int v, String b) { p.set(wx(u, v), oy + y, wz(u, v), r(b)); }

    public void setIfAbsent(int u, int y, int v, String b) { p.setIfAbsent(wx(u, v), oy + y, wz(u, v), r(b)); }

    public void air(int u, int y, int v) { p.air(wx(u, v), oy + y, wz(u, v)); }

    public void remove(int u, int y, int v) { p.remove(wx(u, v), oy + y, wz(u, v)); }

    public String get(int u, int y, int v) { return p.get(wx(u, v), oy + y, wz(u, v)); }

    public boolean has(int u, int y, int v) { return p.has(wx(u, v), oy + y, wz(u, v)); }

    public void fill(int u1, int y1, int v1, int u2, int y2, int v2, String b) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) set(u, y, v, b);
    }

    public void airFill(int u1, int y1, int v1, int u2, int y2, int v2) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) air(u, y, v);
    }
}
