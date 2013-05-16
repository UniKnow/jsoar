/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 1.3.35
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package sml;

import java.util.HashMap;
import java.util.Map;

public class Events
{

    // We keep two maps, one for each direction
    private final Map<Integer, String> m_ToStringMap = new HashMap<Integer, String>();

    private final Map<String, Integer> m_ToEventMap = new HashMap<String, Integer>();

    public synchronized void delete()
    {
    }

    public Events()
    {
    }

    public int ConvertToEvent(String pStr)
    {
        return InternalConvertToEvent(pStr);
    }

    public String ConvertToString(int id)
    {
        return InternalConvertToString(id);
    }

    protected void RegisterEvent(Enum<?> id, String pStr)
    {
        // Neither the id nor the name should be in use already
        assert (InternalConvertToEvent(pStr) == smlGenericEventId.smlEVENT_INVALID_EVENT.ordinal());
        assert (InternalConvertToString(id.ordinal()) == null);

        m_ToStringMap.put(id.ordinal(), pStr);
        m_ToEventMap.put(pStr, id.ordinal());
    }

    /***************************************************************************
     * @brief Convert from a string version of an event to the int (enum)
     *        version. Returns smlEVENT_INVALID_EVENT (== 0) if the string is
     *        not recognized.
     **************************************************************************/
    protected int InternalConvertToEvent(String pStr)
    {
        Integer id = m_ToEventMap.get(pStr);
        return id != null ? id.intValue() : smlGenericEventId.smlEVENT_INVALID_EVENT.ordinal();
    }

    /***************************************************************************
     * @brief Convert from int version of an event to the string form. Returns
     *        NULL if the id is not recognized.
     **************************************************************************/
    protected String InternalConvertToString(int id)
    {
        return m_ToStringMap.get(id);
    }

}