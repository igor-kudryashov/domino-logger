# JUL Handler for logging into Domino document like "Miscellaneous Events" in log.nsf. 
Short example of usage bellow:
```java
import lotus.domino.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaAgent extends AgentBase {
     public static final Logger logger = Logger.getLogger(JavaAgent.class.getName());
 
     public void NotesMain() {
        Session session = getSession();
        try {
           Properties props = new Properties();
            // log name for display document in the view
            props.setProperty("name", "Your logger name");
            // server name of log database
            props.setProperty("server", session.getServerName());
            // filename of log database
            props.setProperty("filename", "log.nsf");
            DominoHandler handler = new DominoHandler(session, props);
            logger.addHandler(handler);
            // (Your code goes here)
            logger.info("This is Info Message ");
            logger.log(Level.WARNING, "Warning Message");
            session.recycle();
        } catch (NotesException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }
 }
```
