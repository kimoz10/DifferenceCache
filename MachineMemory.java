import java.io.*;
import java.util.*;

public class MachineMemory{
	public int unmerge_count = 0;
	public int zeroPage_unmerge_count = 0;
	long total_size;//in bytes
	int zeroPageCount;
	long unmerged_used_space;//size occupied by unmerged pages
	long merged_used_space;//size occupied by merged pages
	//the sum of unmerged_used_space and merged_used_space should always be less than total_size
	ArrayList<ContentTableEntry> refTable;
	public MachineMemory(long total_size){
	//	this.zeroPageCount = zeroPageCount;
		unmerge_count = 0;
		this.total_size = total_size;
		refTable = new ArrayList<ContentTableEntry>();
	}
	public void setZeroPageCount(int n){
		zeroPageCount = n;
	}
	public void addpageList(LinkedList<Page> pl, boolean isZeroPage) throws IOException{
		int mem = pl.get(0).memory_inst;
		File f = new File("./data/sample0.txt");
		int ppn = pl.get(0).ppn;
		FileInputStream fis = new FileInputStream(f);
		byte[] content = new byte[4096];
		fis.skip(ppn*4096);
		fis.read(content);
		ContentTableEntry cte = new ContentTableEntry(pl, content, isZeroPage);
		refTable.add(cte);
		fis.close();
        }
	 public void addpageList2(LinkedList<Page> pl, boolean isZeroPage) throws IOException{
                int mem = pl.get(0).memory_inst;
                //File f = new File("../image"+mem+"/mem"+mem+".dmp");
                int ppn = pl.get(0).ppn;
                //FileInputStream fis = new FileInputStream(f);
                //byte[] content = new byte[4096];
                //fis.skip(ppn*4096);
                //fis.read(content);
                ContentTableEntry cte = new ContentTableEntry(pl, null, isZeroPage);
                refTable.add(cte);
                //fis.close();
        }
	public boolean isZeroPage(int content_idx){
		ContentTableEntry cte = refTable.get(content_idx);
                return cte.isZeroPage();		
	}
}
