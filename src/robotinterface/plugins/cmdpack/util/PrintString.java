/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package robotinterface.plugins.cmdpack.util;

import robotinterface.algorithm.Command;
import robotinterface.algorithm.procedure.Procedure;
import java.util.ArrayList;
import java.util.Arrays;
import org.nfunk.jep.Variable;
import robotinterface.robot.Robot;
import robotinterface.util.trafficsimulator.Clock;

/**
 *
 * @author antunes
 */
public class PrintString extends Procedure {

    private String str;
    private ArrayList<String> varNames;

    public PrintString(String str, String... vars) {
        if (vars != null) {
            varNames = new ArrayList<>();
            varNames.addAll(Arrays.asList(vars));
        }
        this.str = str;
    }

    @Override
    public boolean perform(Robot r, Clock clock) {
        String out = new String(str);
        for (String varName : varNames) {
            Variable v = getParser().getSymbolTable().getVar(varName);
            if (v != null && v.hasValidValue()) {
                out = out.replaceFirst("%v", v.getValue().toString());
            } else {
                out = out.replaceFirst("%v", "¿" + varName + "?");
            }
        }

        System.out.println(out);
        return true;
    }
}
