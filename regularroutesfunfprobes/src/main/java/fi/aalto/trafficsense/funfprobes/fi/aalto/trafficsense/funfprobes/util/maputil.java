package fi.aalto.trafficsense.funfprobes.fi.aalto.trafficsense.funfprobes.util;

import java.util.*;

/**
 * A Map sorting helper directly from stackoverflow:
 * http://stackoverflow.com/questions/109383/how-to-sort-a-mapkey-value-on-the-values-in-java
 *
 * Added here by mikko.rinne@aalto.fi on 03/11/15.
 * MJR modified the original Map -> HashMap and ascending -> descending order.
 */

public class MapUtil
{
    public static <K, V extends Comparable<? super V>> HashMap<K, V>
    sortByValue( HashMap<K, V> map )
    {
        List<Map.Entry<K, V>> list =
                new LinkedList<>( map.entrySet() );
        Collections.sort( list, Collections.reverseOrder(new Comparator<HashMap.Entry<K, V>>()
        {
            @Override
            public int compare( HashMap.Entry<K, V> o1, HashMap.Entry<K, V> o2 )
            {
                return (o1.getValue()).compareTo( o2.getValue() );
            }
        } ) );

        HashMap<K, V> result = new LinkedHashMap<>();
        for (HashMap.Entry<K, V> entry : list)
        {
            result.put( entry.getKey(), entry.getValue() );
        }
        return result;
    }

}
