import java.util.*;
enum PageState{
	MERGED, SEMI_MERGED, UNMERGED	
}
//Merged => Not existing in the diffCache
//Semi_merged => exist in the diff cache
//Umerged => exists in memory. Note if a remerge happens while eviction. page state is merged again

public class Page{
	public int ppn; //physical page number
	public int memory_inst; //either memory 1 or memory 2
	public int content_idx;//idx into a hashmap to get 4 K contents of this page
	public int rank;
	public PageState state;
	public LinkedList<Integer> blocks_in_cache;//list og block numbers belonging to this page that are
	//in the diff cache
	public Page(int ppn, int mem, int idx, PageState state, int rank){
		this.ppn = ppn;
		this.state = state;
		memory_inst = mem;
		content_idx = idx;
		blocks_in_cache = new LinkedList<Integer>();
	}
}
