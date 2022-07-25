package edu.berkeley.cs186.database.query.join;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.iterator.BacktrackingIterator;
import edu.berkeley.cs186.database.query.JoinOperator;
import edu.berkeley.cs186.database.query.QueryOperator;
import edu.berkeley.cs186.database.table.Record;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Performs an equijoin between two relations on leftColumnName and
 * rightColumnName respectively using the Block Nested Loop Join algorithm.
 */
public class BNLJOperator extends JoinOperator {
    protected int numBuffers;

    public BNLJOperator(QueryOperator leftSource,
                        QueryOperator rightSource,
                        String leftColumnName,
                        String rightColumnName,
                        TransactionContext transaction) {
        super(leftSource, materialize(rightSource, transaction),
                leftColumnName, rightColumnName, transaction, JoinType.BNLJ
        );
        this.numBuffers = transaction.getWorkMemSize();
        this.stats = this.estimateStats();
    }

    @Override
    public Iterator<Record> iterator() {
        return new BNLJIterator();
    }

    @Override
    public int estimateIOCost() {
        //This method implements the IO cost estimation of the Block Nested Loop Join
        int usableBuffers = numBuffers - 2;
        int numLeftPages = getLeftSource().estimateStats().getNumPages();
        int numRightPages = getRightSource().estimateIOCost();
        return ((int) Math.ceil((double) numLeftPages / (double) usableBuffers)) * numRightPages +
               getLeftSource().estimateIOCost();
    }

    /**
     * A record iterator that executes the logic for a simple nested loop join.
     * Look over the implementation in SNLJOperator if you want to get a feel
     * for the fetchNextRecord() logic.
     */
    private class BNLJIterator implements Iterator<Record>{
        // Iterator over all the records of the left source
        private Iterator<Record> leftSourceIterator;
        // Iterator over all the records of the right source
        private BacktrackingIterator<Record> rightSourceIterator;
        // Iterator over records in the current block of left pages
        private BacktrackingIterator<Record> leftBlockIterator;
        // Iterator over records in the current right page
        private BacktrackingIterator<Record> rightPageIterator;
        // The current record from the left relation
        private Record leftRecord;
        // The next record to return
        private Record nextRecord;

        private BNLJIterator() {

            super();
            this.leftSourceIterator = getLeftSource().iterator();
            this.fetchNextLeftBlock(); /***/

            this.rightSourceIterator = getRightSource().backtrackingIterator();
            this.rightSourceIterator.markNext();
            this.fetchNextRightPage();/***/
            this.nextRecord = null;
        }

        /**
         Fetch the next block of records from the left source.
         leftBlockIterator should be set to a backtracking iterator over up to
         B-2 pages of records from the left source, and leftRecord should be
         set to the first record in this block.

         If there are no more records in the left source, this method should
         do nothing.
         标记！！！
         You may find QueryOperator#getBlockIterator useful here.
         Make sure you pass in the correct schema to this method.
         */
//        QueryOperator#getBlockIterator
        private void fetchNextLeftBlock() {
            // TODO(proj3_part1): implement
            if(!leftSourceIterator.hasNext()) return ;
            leftBlockIterator = QueryOperator
                    .getBlockIterator(leftSourceIterator, getLeftSource().getSchema(), numBuffers - 2);
            leftBlockIterator.markNext();
            leftRecord = leftBlockIterator.next();
        }

        /**
         * Fetch the next page of records from the right source.
         * rightPageIterator should be set to a backtracking iterator over up to
         * one page of records from the right source.
         *
         * If there are no more records in the right source, this method should
         * do nothing.
         *
         * You may find QueryOperator#getBlockIterator useful here.
         * Make sure you pass in the correct schema to this method.
         */
        private void fetchNextRightPage() {
            // TODO(proj3_part1): implement
            if(!rightSourceIterator.hasNext()) return ;
            rightPageIterator = getBlockIterator(rightSourceIterator, getRightSource().getSchema(), 1);
            rightPageIterator.markNext();
        }

        /**
         * Returns the next record that should be yielded from this join,
         * or null if there are no more records to join.
         *
         * You may find JoinOperator#compare useful here. (You can call compare
         * function directly from this file, since BNLJOperator is a subclass
         * of JoinOperator).
         */

        private Record fetchNextRecord() {
            // TODO(proj3_part1): implement
            Record rightRecord;
            while (true) {
                if (rightPageIterator.hasNext()) { // 右迭代器有值
                } else if (leftBlockIterator.hasNext()) { // 右迭代器无值===>说明，目前的左值已经在这块匹配完了，而左有值那么
                    leftRecord = leftBlockIterator.next();//下一个左值和这个有块儿重新匹配！！！
                    rightPageIterator.reset(); //每次reset都是回到块开始的地方
                } else if (rightSourceIterator.hasNext()) { //都没有值了，但是还有右页，那么将左块reset，继续匹配
                    leftBlockIterator.reset();
                    leftRecord = leftBlockIterator.next();
                    fetchNextRightPage();
                } else if (leftSourceIterator.hasNext()) { //都无值，且无页仍右块，说明这一块已经匹配完了
                    fetchNextLeftBlock(); // 移到下一块儿，然后reset右源头，从第一个右页开始匹配
                    rightSourceIterator.reset();
                    fetchNextRightPage();
                } else {
                    return null;
                }
                rightRecord = rightPageIterator.next();
                if (compare(leftRecord, rightRecord) == 0)
                    return leftRecord.concat(rightRecord);
            }
        }
        /**
         * @return true if this iterator has another record to yield, otherwise
         * false
         */
        @Override
        public boolean hasNext() {
            if (this.nextRecord == null) this.nextRecord = fetchNextRecord();
            return this.nextRecord != null;
        }

        /**
         * @return the next record from this iterator
         * @throws NoSuchElementException if there are no more records to yield
         */
        @Override
        public Record next() {
            if (!this.hasNext()) throw new NoSuchElementException();
            Record nextRecord = this.nextRecord;
            this.nextRecord = null;
            return nextRecord;
        }
    }
}