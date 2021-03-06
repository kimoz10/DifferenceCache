import java.util.*;
import java.io.*;
public abstract class Cache{
	public MachineMemory mm;//The Machine memory to which "this" cache belongs
	public int unmerge_count;
	public boolean isRemerge;
	public int threshold_unmerge_count;
	public int full_set_unmerge_count;
	public int remerge_count;
	public int renegade_count;
	public int occupied_block_count;
	public int blockSize;//in bytes
	public int blockCount;//number of blocks
	public int page_block_count_threshold;//maximum number of blocks that a given page object can inject into the cache
	public synchronized int addCacheEntry(CacheEntry ce) throws IOException{
		System.out.println("ERROR in the abstract class method");
		return -1;
	}
	HashMap<Page, ArrayList<CacheEntry>> cache_table;
		
}
