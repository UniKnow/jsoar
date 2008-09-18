/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 17, 2008
 */
package org.jsoar.kernel.rhs;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jsoar.kernel.symbols.SymConstant;
import org.jsoar.kernel.symbols.Variable;

/**
 * @author ray
 */
public class RhsFunctionCall extends RhsValue
{
    private SymConstant name;
    private boolean standalone;
    private List<RhsValue> arguments = new ArrayList<RhsValue>();
    
    /**
     * @param name
     */
    public RhsFunctionCall(SymConstant name, boolean standalone)
    {
        this.name = name;
    }
    
    private RhsFunctionCall(RhsFunctionCall other)
    {
        this.name = other.name;
        this.standalone = other.standalone;
        for(RhsValue arg : other.arguments)
        {
            this.arguments.add(arg.copy());
        }
    }

    public SymConstant getName()
    {
        return name;
    }
    
    
    /**
     * @return the standalone
     */
    public boolean isStandalone()
    {
        return standalone;
    }

    public void addArgument(RhsValue arg)
    {
        arguments.add(arg);
    }

    public List<RhsValue> getArguments()
    {
        return arguments;
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.RhsValue#asFunctionCall()
     */
    @Override
    public RhsFunctionCall asFunctionCall()
    {
        return this;
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.RhsValue#copy()
     */
    @Override
    public RhsValue copy()
    {
        return new RhsFunctionCall(this);
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.RhsValue#addAllVariables(int, java.util.List)
     */
    @Override
    public void addAllVariables(int tc_number, LinkedList<Variable> var_list)
    {
        for(RhsValue arg : arguments)
        {
            arg.addAllVariables(tc_number, var_list);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        return "(name " + arguments + ")";
    }
    
    
    
}
