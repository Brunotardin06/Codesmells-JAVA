/*
 *  “Lean” connection figure – keeps daily-use features, drops the extras.
 *
 *  ▸ Conserves the geometry/painting logic inherited from LineFigure.
 *  ▸ Retains just enough behaviour to be practical in an editor:
 *      • start / end connector storage
 *      • automatic update when either connector or figure moves
 *      • simple endpoint handles
 *      • optional reverseConnection( ) helper
 *  ▸ Adds **XML persistence** (read / write with DOMInput/DOMOutput).
 *  ▸ Ignores the heavyweight undo/event machinery that existed in the
 *    original implementation – the unused inheritance constitutes the
 *    **Refused Bequest** requested.
 */
package org.jhotdraw.draw;

import org.jhotdraw.xml.*;

import java.awt.geom.*;
import java.io.IOException;
import java.util.*;

public class LineConnectionFigure
        extends LineFigure
        implements ConnectionFigure {

    //  Connection-specific state                                     
    private Connector start;
    private Connector end;

    public LineConnectionFigure() { super(); }

    //  Basic accessors                                               
    public Connector getStartConnector()            { return start; }
    public Connector getEndConnector()              { return end;   }
    public void      setStartConnector(Connector c) { start = c;    }
    public void      setEndConnector  (Connector c) { end   = c;    }
                
    //  Keep endpoints glued to their connectors                     
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
                
    @Override
    protected void basicTransform(AffineTransform tx) {
        super.basicTransform(tx);
        updateConnection();
    }

   
    // Helper: swap origin and destination                          
    public void reverseConnection() {
        if (start == null || end == null) return;

        Connector tmp = start; start = end; end = tmp;

        Point2D.Double a = getStartPoint();
        Point2D.Double b = getEndPoint();
        basicSetStartPoint(b);
        basicSetEndPoint(a);
    }
    //  Interaction                                                   
    public boolean canConnect() { return false; }

    @Override
    public Collection<Handle> createHandles(int level) {
        if (level != 0) return Collections.emptyList();
        return List.of(
            new ChangeConnectionStartHandle(this),
            new ChangeConnectionEndHandle(this)
        );
    }
    // XML persistence
    @Override
    public void write(DOMOutput out) throws IOException {
        super.write(out);                         // geometry & styling
        out.openElement("startConnector");
        out.writeObject(start);
        out.closeElement();
        out.openElement("endConnector");
        out.writeObject(end);
        out.closeElement();
    }
    @Override            
    public void read(DOMInput in) throws IOException {
        super.read(in);
        if (in.getElementCount("startConnector") > 0) {
            in.openElement("startConnector");
            start = (Connector) in.readObject();
            in.closeElement();
        }
        if (in.getElementCount("endConnector") > 0) {
            in.openElement("endConnector");
            end = (Connector) in.readObject();
            in.closeElement();
        }
        updateConnection();                       // ensure geometry is valid
    }

    
}
