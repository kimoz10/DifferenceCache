import java.util.*;
public class CacheEntryComparator implements Comparator<CacheEntry>{

	public int compare(CacheEntry ce1, CacheEntry ce2){
		if(ce1.block_number == ce2.block_number) return 0;
		else if(ce1.block_number<ce2.block_number) return -1;
		else return 1;
	}
}
