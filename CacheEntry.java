public class CacheEntry{
  //ZZpublic int Blocksize;//bytes
  public int set;
  public Page page;
  public int block_number;//block number within a page
  public byte[] content;//contents of the block
  // add more properties as required later
  public CacheEntry(Page p, int bn, byte[] c){
	page = p;
	block_number = bn;
	content = c;	
  }
  public void AssignSet(int s){
	set = s;
  }
}
