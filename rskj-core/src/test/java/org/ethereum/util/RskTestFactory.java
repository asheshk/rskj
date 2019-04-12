package org.ethereum.util;

import co.rsk.RskContext;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.Genesis;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.vm.PrecompiledContracts;

/**
 * This is the test version of {@link RskContext}.
 * <p>
 * We try to recreate the objects used in production as best as we can,
 * replacing persistent storage with in-memory storage.
 * There are many nulls in place of objects that aren't part of our
 * tests yet.
 */
public class RskTestFactory extends RskTestContext {
    private final TestSystemProperties config;

    private BlockExecutor blockExecutor;
    private BlockExecutor.TransactionExecutorFactory transactionExecutorFactory;
    private PrecompiledContracts precompiledContracts;

    public RskTestFactory() {
        this(new TestSystemProperties());
    }

    public RskTestFactory(TestSystemProperties config) {
        super(new String[0]);
        this.config = config;
    }

    @Override
    public RskSystemProperties buildRskSystemProperties() {
        return config;
    }

    @Override
    public BlockValidator buildBlockValidator() {
        return new DummyBlockValidator();
    }

    @Override
    public Genesis buildGenesis() {
        return new BlockGenerator().getGenesisBlock();
    }

    @Override
    public SyncConfiguration buildSyncConfiguration() {
        return SyncConfiguration.IMMEDIATE_FOR_TESTING;
    }

    public BlockExecutor getBlockExecutor() {
        if (blockExecutor == null) {
            blockExecutor = new BlockExecutor(
                    getRepository(),
                    getTransactionExecutorFactory(),
                    getStateRootHandler()
            );
        }

        return blockExecutor;
    }

    private BlockExecutor.TransactionExecutorFactory getTransactionExecutorFactory() {
        if (transactionExecutorFactory == null) {
            RskSystemProperties config = getRskSystemProperties();
            transactionExecutorFactory = (tx, txindex, coinbase, track, block, totalGasUsed) -> new TransactionExecutor(
                    tx,
                    txindex,
                    block.getCoinbase(),
                    track,
                    getBlockStore(),
                    getReceiptStore(),
                    getProgramInvokeFactory(),
                    block,
                    getCompositeEthereumListener(),
                    totalGasUsed,
                    config.getVmConfig(),
                    config.getBlockchainConfig(),
                    config.playVM(),
                    config.isRemascEnabled(),
                    config.vmTrace(),
                    getPrecompiledContracts(),
                    config.databaseDir(),
                    config.vmTraceDir(),
                    config.vmTraceCompressed()
            );
        }

        return transactionExecutorFactory;
    }

    private PrecompiledContracts getPrecompiledContracts() {
        if (precompiledContracts == null) {
            precompiledContracts = new PrecompiledContracts(getRskSystemProperties());
        }

        return precompiledContracts;
    }

    public static Genesis getGenesisInstance(RskSystemProperties config) {
        return GenesisLoader.loadGenesis(config.genesisInfo(), config.getBlockchainConfig().getCommonConstants().getInitialNonce(), false);
    }
}
