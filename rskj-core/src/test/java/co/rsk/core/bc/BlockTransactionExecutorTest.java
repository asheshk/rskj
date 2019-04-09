/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core.bc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.util.RLP;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Created by ajlopez on 09/04/2019.
 */
public class BlockTransactionExecutorTest {
    public static final byte[] EMPTY_TRIE_HASH = sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    private static final TestSystemProperties config = new TestSystemProperties();

    private Blockchain blockchain;
    private BlockTransactionExecutor executor;
    private Repository repository;
    private BlockExecutorTest.SimpleEthereumListener listener;

    @Before
    public void setUp() {
        RskTestFactory objects = new RskTestFactory(config) {
            @Override
            public CompositeEthereumListener buildCompositeEthereumListener() {
                return new BlockExecutorTest.SimpleEthereumListener();
            }
        };

        blockchain = objects.getBlockchain();
        executor = objects.getBlockTransactionExecutor();
        repository = objects.getRepository();
        listener = (BlockExecutorTest.SimpleEthereumListener) objects.getCompositeEthereumListener();
    }

    @Test
    public void executeBlockWithoutTransaction() {
        Block parent = blockchain.getBestBlock();
        Block block = new BlockGenerator().createChildBlock(parent);

        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertTrue(result.getTransactionReceipts().isEmpty());
        Assert.assertArrayEquals(repository.getRoot(), parent.getStateRoot());
        Assert.assertArrayEquals(repository.getRoot(), result.getStateRoot());
    }

    @Test
    public void executeBlockWithOneTransaction() {
        Block block = getBlockWithOneTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();

        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);
        Assert.assertNotNull(listener.getLatestSummary());
        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(21000, result.getGasUsed());
        Assert.assertEquals(21000, result.getPaidFees().asBigInteger().intValueExact());

        Assert.assertNotNull(result.getReceiptsRoot());
        Assert.assertArrayEquals(BlockChainImpl.calcReceiptsTrie(result.getTransactionReceipts()), result.getReceiptsRoot());

        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getStateRoot()));

        Assert.assertNotNull(result.getLogsBloom());
        Assert.assertEquals(256, result.getLogsBloom().length);
        for (int k = 0; k < result.getLogsBloom().length; k++) {
            Assert.assertEquals(0, result.getLogsBloom()[k]);
        }

        AccountState accountState = repository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000), accountState.getBalance().asBigInteger());

        Repository finalRepository = repository.getSnapshotTo(result.getStateRoot());

        accountState = finalRepository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(30000 - 21000 - 10), accountState.getBalance().asBigInteger());
    }

    @Test
    public void executeBlockWithTwoTransactions() {
        Block block = getBlockWithTwoTransactions(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx1 = block.getTransactionsList().get(0);
        Transaction tx2 = block.getTransactionsList().get(1);
        RskAddress account = tx1.getSender();

        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);

        Assert.assertNotNull(result.getTransactionReceipts());
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(2, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx1, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(21000, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        receipt = result.getTransactionReceipts().get(1);
        Assert.assertEquals(tx2, receipt.getTransaction());
        Assert.assertEquals(21000, new BigInteger(1, receipt.getGasUsed()).longValue());
        Assert.assertEquals(42000, BigIntegers.fromUnsignedByteArray(receipt.getCumulativeGas()).longValue());
        Assert.assertTrue(receipt.hasTxStatus() && receipt.isTxStatusOK() && receipt.isSuccessful());

        Assert.assertEquals(42000, result.getGasUsed());
        Assert.assertEquals(42000, result.getPaidFees().asBigInteger().intValueExact());

        Assert.assertNotNull(result.getReceiptsRoot());
        Assert.assertArrayEquals(BlockChainImpl.calcReceiptsTrie(result.getTransactionReceipts()), result.getReceiptsRoot());
        Assert.assertFalse(Arrays.equals(repository.getRoot(), result.getStateRoot()));

        Assert.assertNotNull(result.getLogsBloom());
        Assert.assertEquals(256, result.getLogsBloom().length);
        for (int k = 0; k < result.getLogsBloom().length; k++)
            Assert.assertEquals(0, result.getLogsBloom()[k]);

        AccountState accountState = repository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(60000), accountState.getBalance().asBigInteger());

        Repository finalRepository = repository.getSnapshotTo(result.getStateRoot());

        accountState = finalRepository.getAccountState(account);

        Assert.assertNotNull(accountState);
        Assert.assertEquals(BigInteger.valueOf(60000 - 42000 - 20), accountState.getBalance().asBigInteger());
    }

    private Block getBlockWithOneTransaction() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(30000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        List<Transaction> txs = Collections.singletonList(
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()))
        );

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private Block getBlockWithTwoTransactions() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(60000));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Assert.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, repository.getRoot()));

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        List<Transaction> txs = Arrays.asList(
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress())),
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()).add(BigInteger.ONE))
        );

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private static Transaction createTransaction(Account sender, Account receiver, BigInteger value, BigInteger nonce) {
        String toAddress = Hex.toHexString(receiver.getAddress().getBytes());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        Transaction tx = new Transaction(config, toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(21000));
        tx.sign(privateKeyBytes);
        return tx;
    }

    public static Account createAccount(String seed, Repository repository, Coin balance) {
        Account account = createAccount(seed);
        repository.createAccount(account.getAddress());
        repository.addBalance(account.getAddress(), balance);
        return account;
    }

    public static Account createAccount(String seed) {
        byte[] privateKeyBytes = HashUtil.keccak256(seed.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account = new Account(key);
        return account;
    }

    private static byte[] sha3(byte[] input) {
        Keccak256 digest =  new Keccak256();
        digest.update(input);
        return digest.digest();
    }
}
