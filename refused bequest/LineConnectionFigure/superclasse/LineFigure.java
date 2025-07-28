package org.jhotdraw.draw;

import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Collections;

/**
 * A very slimmed‑down connection that inherits all the geometry and
 * painting behavior from {@link LineFigure} but keeps the
 * connection‑specific state to an absolute minimum
 * (start/end connector references plus a trivial update routine).
 *
 * Everything else that the superclass offers is left untouched and
 * unused — on purpose — to illustrate the “Refused Bequest” case
 * where a subclass takes only a tiny slice of the inherited
 * functionality. 
 */
public class LineConnectionFigure extends LineFigure implements ConnectionFigure {

    private Connector start;
    private Connector end;

    /* basic constructor */
    public LineConnectionFigure() {
        super();
    }

    /* connection‑specific API – bare minimum */
    public Connector getStartConnector()      { return start; }
    public Connector getEndConnector()        { return end;   }
    public void setStartConnector(Connector c){ start = c;    }
    public void setEndConnector(Connector c)  { end   = c;    }

    public void updateConnection() {
        if (start != null) {
            Point2D.Double p = start.findStart(this);
            if (p != null) basicSetStartPoint(p);
        }
        if (end != null) {
            Point2D.Double p = end.findEnd(this);
            if (p != null) basicSetEndPoint(p);
        }
    }

    /* figures that are themselves connections cannot be targets */
    public boolean canConnect() { return false; }

    /* no editing support besides the two endpoints */
    public Collection<Handle> createHandles(int level) {
        return Collections.emptyList();
    }
}
