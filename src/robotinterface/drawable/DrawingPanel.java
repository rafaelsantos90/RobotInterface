/**
 * @file .java
 * @author Anderson Antunes <anderson.utf@gmail.com>
 *         *seu nome* <*seu email*>
 * @version 1.0
 *
 * @section LICENSE
 *
 * Copyright (C) 2013 by Anderson Antunes <anderson.utf@gmail.com>
 *                       *seu nome* <*seu email*>
 *
 * RobotInterface is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * RobotInterface is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * RobotInterface. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package robotinterface.drawable;

import robotinterface.drawable.util.QuickFrame;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JPanel;
import robotinterface.drawable.Drawable;
import robotinterface.drawable.DWidgetContainer.Widget;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import robotinterface.util.trafficsimulator.Clock;

/**
 * Painel para desenho de componentes desenháveis.
 */
public class DrawingPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener, MouseWheelListener, ActionListener, ComponentListener, Drawable {

    public final double MIN_ZOOM = 0.1;
    public final double MAX_ZOOM = 10.0;
    protected final long PAINT_DELAY = 2;
    protected final long NO_PAINT_DELAY = 100;
    protected final Clock clock;
    private final ArrayList<Drawable> objects;
    private final ArrayList<Integer> keys;
    private final Point mouse;
    private Thread repaintThread;
    private BufferedImage buffer;
    private boolean repaint = false;
    private int width;
    private int height;
    private int globalX = 0, globalY = 0;
    private double zoom = 1.0;
    private boolean autoFullSize = false;
    private Rectangle2D.Double bounds;
    //********** componente atual
    private AffineTransform originalTransform;
    private AffineTransform currentTransform;
    private Shape currentBounds;
    private boolean beginDrawing = false;
    private boolean mouseClick = false;
    private Drawable currentObject;
    private int objectX = 0;
    private int objectY = 0;
    private GraphicAttributes currentGraphicAtributes;
    private InputState currentInputState;

    public DrawingPanel(Clock c) {
        super(true);
        //tamanho preferencial
        this.setPreferredSize(new Dimension(640, 480));
        //ATENÇÂO! setFocusable é sempre ignorado em algum lugar 
        //(netbeans, implementação, etc), e isso faz com que 
        //KeyListener não funcione!
        this.setFocusable(true);
        //componentes Swing em toda parte
        this.setLayout(null);

        mouse = new Point();
        clock = c;
        objects = new ArrayList<>();
        keys = new ArrayList<>();
        repaintThread = null;
        bounds = new Rectangle2D.Double();
        currentGraphicAtributes = new GraphicAttributes();
        currentInputState = new InputState();
        currentTransform = new AffineTransform();
        currentBounds = new Rectangle();

        //testes, descomente e veja o que acontece
//        add(new DrawableTest());
//        Circle cw = new Circle();
//        cw.setObjectBounds(10, 10, 30, 30);
//        add(cw);

        //adiciona listeners
        addListeners();
        //inicia a thread de desenho
        play();
    }

    public DrawingPanel(Clock c, boolean autoFullSize) {
        this(c);
        this.autoFullSize = autoFullSize;
    }

    @Deprecated
    public DrawingPanel() {
        this(new Clock());
    }

    private void addListeners() {
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(this);
        this.addKeyListener(this);
        this.add((Drawable) this);
    }

    protected final void createBuffers() {
        width = this.getWidth();
        height = this.getHeight();
        //cria buffers no padrão do sistema (teoricamente mais eficiente)
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gs = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gs.getDefaultConfiguration();
        buffer = gc.createCompatibleImage(width, height, Transparency.OPAQUE);
    }

    public final void play() {
        clock.setPaused(false);
        //cria thread para pintar a tela
        if (repaintThread == null) {
            repaintThread = new Thread("Repaint Thread- " + Thread.activeCount()) {
                @Override
                public void run() {
                    try {
                        while (true) {
                            if (repaint) {
                                repaint();
                                Thread.sleep(PAINT_DELAY);
                            } else {
                                Thread.sleep(NO_PAINT_DELAY);
                            }
                        }
                    } catch (InterruptedException ex) {
                        repaintThread = null;
                    }
                }
            };
            repaintThread.start();
        }

        repaint = true;
    }

    public final void pause() {
        repaint = false;
    }

    public final void add(Drawable d) {
        synchronized (objects) {
            objects.add(d);
        }
        if (d instanceof DWidgetContainer) {
            ((DWidgetContainer) d).appendTo(this);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        //super.paintComponent(g); //não usar

        //ignora chamadas antes do buffer ser construido
        if (buffer == null) {
            createBuffers();
            return;
        }

        //desenha o fundo dentro do buffer
        Graphics g1 = buffer.getGraphics();
        g1.setColor(Color.WHITE);
        g1.fillRect(0, 0, width, height);

        //desenha o buffer no painel
        g.drawImage(buffer, 0, 0, null);

        //relogio global
        clock.increase();

        //converte Graphics para Graphics2D
        Graphics2D g2 = (Graphics2D) g;
        originalTransform = g2.getTransform();

        //deixa tudo lindo (antialiasing)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        beginDrawing = mouseClick;

        //desenha fundo
        synchronized (objects) {
            for (Drawable d : objects) {
                currentObject = d;
                if ((d.getDrawableLayer() & BACKGROUND_LAYER) != 0) {
                    currentTransform.setTransform(originalTransform);
                    g2.setTransform(currentTransform);
                    currentTransform.translate(globalX, globalY);
                    currentTransform.scale(zoom, zoom);
                    currentTransform.translate(d.getObjectBouds().x, d.getObjectBouds().y);
                    g2.setTransform(currentTransform);
                    d.drawBackground(g2, currentGraphicAtributes, currentInputState);
                }
                currentObject = null;
            }
        }

        //desenha coisas
        synchronized (objects) {
            for (Drawable d : objects) {
                currentObject = d;
                if ((d.getDrawableLayer() & DEFAULT_LAYER) != 0) {
                    currentTransform.setTransform(originalTransform);
                    g2.setTransform(currentTransform);
                    currentTransform.translate(globalX, globalY);
                    currentTransform.scale(zoom, zoom);
                    currentBounds = currentTransform.createTransformedShape(d.getObjectShape());
                    currentTransform.translate(d.getObjectBouds().x, d.getObjectBouds().y);
                    //g2.setClip(currentBounds); usar limite de pintura
                    g2.setTransform(currentTransform);
                    d.draw(g2, currentGraphicAtributes, currentInputState);
                }
                currentObject = null;
            }
        }

        //desenha primeiro plano (sem posição global e zoom)
        synchronized (objects) {
            for (Drawable d : objects) {
                currentObject = d;
                if ((d.getDrawableLayer() & TOP_LAYER) != 0) {
                    currentTransform.setTransform(originalTransform);
                    currentTransform.translate(d.getObjectBouds().x, d.getObjectBouds().y);
                    g2.setTransform(currentTransform);
                    d.drawTopLayer(g2, currentGraphicAtributes, currentInputState);
                }
                currentObject = null;
            }
        }

        //reseta o zoom e posição para desenhar os componentes swing
        g2.setTransform(originalTransform);
        g2.setClip(0, 0, width, height);

        //redefine o tamanho e a posição dos componentes swing
        synchronized (objects) {
            for (Drawable d : objects) {
                if (d instanceof DWidgetContainer) {
                    DWidgetContainer dwc = (DWidgetContainer) d;
                    for (Widget c : dwc) {
                        if (!dwc.isWidgetVisible()) {
                            c.getJComponent().setVisible(false);
                            continue;
                        }

                        //ativando double buffer e fundo transparente
                        c.getJComponent().setVisible(true);
                        c.getJComponent().setDoubleBuffered(true);
                        c.getJComponent().setOpaque(false);
                        c.getJComponent().revalidate();

                        //tamanho do componente swing
                        currentTransform.setToIdentity();
                        currentTransform.translate(globalX, globalY);
                        currentTransform.scale(zoom, zoom);
                        currentTransform.translate(d.getObjectBouds().x, d.getObjectBouds().y);
                        c.getJComponent().setBounds(currentTransform.createTransformedShape(c.getBounds()).getBounds());
                    }
                }
            }
        }

        if (beginDrawing) {
            beginDrawing = false;
            mouseClick = false;
        }

    }

    @Override
    public final void keyTyped(KeyEvent e) {
        synchronized (keys) {
            if (e.getKeyCode() != 0 && !keys.contains(e.getKeyCode())) {
                keys.add(e.getKeyCode());
            }
        }
    }

    @Override
    public final void keyPressed(KeyEvent e) {
        synchronized (keys) {
            if (e.getKeyCode() != 0 && !keys.contains(e.getKeyCode())) {
                keys.add(e.getKeyCode());
            }
        }
    }

    @Override
    public final void keyReleased(KeyEvent e) {
        synchronized (keys) {
            while (keys.contains(e.getKeyCode())) {
                keys.remove(keys.indexOf(e.getKeyCode()));
            }
        }
    }

    @Override
    public void mouseClicked(final MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            mouseClick = true;
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (e.getComponent() != this) {
            return;
        }
        synchronized (mouse) {
            //define a posição relativa com base no deslocamento do mouse
            setPosition((int) (mouse.getX() - e.getPoint().getX()), (int) (mouse.getY() - e.getPoint().getY()));
            mouse.setLocation(e.getPoint());
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (e.getComponent() != this) {
            Rectangle r = e.getComponent().getBounds();
            Point tmp = e.getPoint();
            tmp.translate(r.x, r.y);

            mouse.setLocation(tmp);
            return;
        }
        synchronized (mouse) {
            mouse.setLocation(e.getPoint());
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        setZoom(e.getWheelRotation() * 0.1, e.getPoint());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        this.repaint();
    }

    @Override
    public void componentResized(ComponentEvent e) {
        if (autoFullSize) {
            width = e.getComponent().getWidth();
            height = e.getComponent().getHeight();
            this.setSize(width, height);
        }
        createBuffers();
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    public synchronized void setZoom(double z, Point pos) {
        if ((zoom + z) >= MIN_ZOOM && (zoom + z) <= MAX_ZOOM) {

            Point posInside = getMouse(pos);

            if (new Rectangle(0, 0, width, height).contains(posInside)) {
                globalX -= (int) (((pos.getX() - globalX) / zoom) * z);
                globalY -= (int) (((pos.getY() - globalY) / zoom) * z);
            } else {
                globalX -= (int) (width * z / 2.0);
                globalY -= (int) (height * z / 2.0);
            }

            zoom = zoom + z;
        }
    }

    public synchronized double getZoom() {
        return zoom;
    }

    public synchronized Point getPosition() {
        return new Point(globalX, globalY);
    }

    public synchronized void setPosition(int x, int y) {
        globalX -= x;
        globalY -= y;
    }

    public synchronized void center(Rectangle clip) {
        globalX = (int) (clip.width * zoom / 2.0 - width * zoom / 2.0);
        globalY = (int) (clip.height * zoom / 2.0 - height * zoom / 2.0);
    }

    public synchronized Point getMouse(Point mouse) {
        return new Point((int) ((mouse.getX() - globalX) / zoom), (int) ((mouse.getY() - globalY) / zoom));
    }

    public static void drawGrade(Graphics2D g, int grid, float p, Rectangle bounds) {
        if (grid > 0) {

            int prop = (int) p / grid;

            for (int x = -(bounds.width / prop) / 2; x <= (bounds.width / prop) / 2; x++) {
                g.drawLine(x * prop, -bounds.height / 2, x * prop, bounds.height / 2);
            }

            for (int y = -(bounds.height / prop) / 2; y <= (bounds.height / prop) / 2; y++) {
                g.drawLine(-bounds.width / 2, y * prop, bounds.width / 2, y * prop);
            }

        } else if (grid < 0) {
            int prop = (int) p / -grid;

            for (int x = -(bounds.width / prop) / 2; x <= (bounds.width / prop) / 2; x++) {
                for (int y = -(bounds.height / prop) / 2; y <= (bounds.height / prop) / 2; y++) {
                    g.fillRect(x * prop - 1, y * prop - 1, 2, 2);
                }
            }

        } else {
            return;
        }
        String str = "grade: " + Math.abs(1.0f / grid) * 100 + " cm";
        int sx = g.getFontMetrics().stringWidth(str);
        int sy = g.getFontMetrics().getHeight();
        int px = bounds.width / 2 - 10 - sx;
        int py = bounds.height / 2 - 20;
        g.setColor(Color.lightGray);
        g.fillRect(px, py - 11, sx, sy);
        g.setColor(Color.white);
        g.drawString(str, px, py);

    }

    @Override
    public int getDrawableLayer() {
        return 0;
    }

    @Override
    public void drawBackground(Graphics2D g, GraphicAttributes ga, InputState in) {
    }

    @Override
    public void draw(Graphics2D g, GraphicAttributes ga, InputState in) {
    }

    @Override
    public void drawTopLayer(Graphics2D g, GraphicAttributes ga, InputState in) {
    }

    @Override
    public Shape getObjectShape() {
        return getObjectBouds();
    }

    @Override
    public Rectangle2D.Double getObjectBouds() {
        bounds.width = width;
        bounds.height = height;
        return bounds;
    }

    @Override
    public void setObjectLocation(double x, double y) {
        bounds.x = x;
        bounds.y = y;
    }

    @Override
    public void setObjectBounds(double x, double y, double width, double height) {
        bounds.x = x;
        bounds.y = y;
        bounds.width = width;
        bounds.height = height;
    }

    public class GraphicAttributes {

        private GraphicAttributes() {
        }

        public Clock getClock() {
            return clock;
        }

        public void applyZoom(AffineTransform t) {
            t.scale(zoom, zoom);
        }

        public void removeZoom(AffineTransform t) {
            t.scale(1 / zoom, 1 / zoom);
        }

        public void applyGlobalPosition(AffineTransform t) {
            t.translate(globalX, globalY);
        }

        public void removeGlobalPosition(AffineTransform t) {
            t.translate(-globalX, -globalY);
        }

        public void reset(AffineTransform t) {
            t.setToIdentity();
            t.translate(objectX, objectY);
        }
    }

    public class InputState {

        private InputState() {
        }

        public boolean isKeyPressed(int key) {
            synchronized (keys) {
                return keys.contains(key);
            }
        }

        public int getSingleKey() {
            synchronized (keys) {
                if (!keys.isEmpty()) {
                    return keys.get(0);
                } else {
                    return -1;
                }
            }
        }

        public boolean isMouseOver() {
            synchronized (mouse) {
                return currentBounds.contains(mouse);
            }
        }

        public boolean mouseClicked() {
            synchronized (mouse) {
                if (mouseClick && beginDrawing && currentBounds.contains(mouse)) {
                    return true;
                }
                return false;
            }
        }

        public Point getMouse() {
            synchronized (mouse) {
                return new Point(mouse);//mudar para posição relativa?
            }
        }
    }

    public static void main(String[] args) {
        DrawingPanel p = new DrawingPanel();
        QuickFrame.create(p, "Teste do painel de desenho").addComponentListener(p);
        System.err.println("Não se esqueça de alterar getDrawableLayer() se for desenhar algo nesse painel!!");
    }
}
