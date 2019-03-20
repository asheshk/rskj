/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.cli.migration;

import co.rsk.RskContext;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.net.TestNetConfig;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnitrieComparator {

    static {
        SystemProperties.DONOTUSE_blockchainConfig = new TestNetConfig();
    }

    public static void main(String[] args) {
        Trie migratedUnitrie = getMigratedUnitrie();
        Trie replayedUnitrie = getReplayedUnitrie();

//        int migratedValues = countValueNodes(migratedUnitrie);
//        int replayedValues = countValueNodes(replayedUnitrie);
//        System.out.printf("Migrated values: %d\nReplayed values: %d\n", migratedValues, replayedValues);

//        Set<RskAddress> migratedAddresses = extractAccountsFromUnitrie(migratedUnitrie);
//        Set<RskAddress> replayedAddresses = extractAccountsFromUnitrie(replayedUnitrie);
//        System.out.printf("Migrated addresses: %d\nReplayed addresses: %d", migratedAddresses.size(), replayedAddresses.size());

//        Set<Keccak256> migratedAccountsHashes = extractAccountsHashesFromUnitrie(migratedUnitrie);
//        Set<Keccak256> replayedAccountsHashes = extractAccountsHashesFromUnitrie(replayedUnitrie);
//        System.out.printf("Has same account hashes: %s\n", migratedAccountsHashes.equals(replayedAccountsHashes));

//        Set<DataWord> migratedStorageKeys = extractStorageKeys(migratedUnitrie);
//        Set<DataWord> replayedStorageKeys = extractStorageKeys(replayedUnitrie);
//        System.out.printf("Migrated addresses: %d\nReplayed addresses: %d\n", migratedStorageKeys.size(), replayedStorageKeys.size());
//        System.out.printf("Contains all: %s\n", replayedStorageKeys.containsAll(migratedStorageKeys));
//        replayedStorageKeys.removeAll(migratedStorageKeys);
//        System.out.printf("{Replayed - Migrated} addresses: %d\n", replayedStorageKeys.size());

//        Set<Keccak256> migratedStorageHashes = extractStorageHashes(migratedUnitrie);
//        Set<Keccak256> replayedStorageHashes = extractStorageHashes(replayedUnitrie);
//        System.out.printf("Has same storage hashes: %s\n", migratedStorageHashes.equals(replayedStorageHashes));

//        findFirstDifference(migratedUnitrie, replayedUnitrie);

        Map<ByteBuffer, byte[]> migratedValues = extractAllValues(migratedUnitrie);
        System.out.println("=======");
        Map<ByteBuffer, byte[]> replayedValues = extractAllValues(replayedUnitrie);

        System.out.printf("Migrated values: %d\nReplayed values: %d\n", migratedValues.size(), replayedValues.size());

        replayedValues.keySet().removeAll(migratedValues.keySet());
        Set<ByteBuffer> differentValues = replayedValues.values().stream().map(ByteBuffer::wrap).collect(Collectors.toSet());
        Map<ByteBuffer, Long> counted = replayedValues.values().stream().map(ByteBuffer::wrap)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<Long> repetitionsCount = counted.values().stream().distinct().collect(Collectors.toList());
        System.out.printf("Repetitions: %s\n", repetitionsCount);
//        for (Map.Entry<ByteBuffer, Long> byteBufferLongEntry : counted.entrySet()) {
//            System.out.printf("%s : %d\n", Hex.toHexString(byteBufferLongEntry.getKey().array()), byteBufferLongEntry.getValue());
//        }
        for (Map.Entry<ByteBuffer, Long> byteBufferLongEntry : counted.entrySet()) {
            if (byteBufferLongEntry.getValue() == 9169) {
                System.out.printf("9169 appareances of %s\n", Hex.toHexString(byteBufferLongEntry.getKey().array()));
            }
        }
        System.out.printf("Missing migrated keys: %d\n", differentValues.size());
    }

    private static boolean findFirstDifference(Trie migratedUnitrie, Trie replayedUnitrie) {

        int equalsCounter = 0;

        Iterator<Trie.IterationElement> migratedIterator = migratedUnitrie.getPostOrderIterator();
        Iterator<Trie.IterationElement> replayedIterator = replayedUnitrie.getPostOrderIterator();

        while (migratedIterator.hasNext() && replayedIterator.hasNext()) {
            Trie.IterationElement migratedIterationElement = migratedIterator.next();
            Trie.IterationElement replayedIterationElement = replayedIterator.next();
            if (!migratedIterationElement.getNode().getHash().equals(replayedIterationElement.getNode().getHash())) {
                System.out.printf("After %d nodes we found a difference in post-order.\nMigrated key: %s\nReplayed key: %s",
                    equalsCounter,
                    Hex.toHexString(migratedIterationElement.getNodeKey().encode()),
                    Hex.toHexString(replayedIterationElement.getNodeKey().encode())
                );
                return false;
            }
            equalsCounter++;
        }

        return true;
    }

    private static int countValueNodes(Trie unitrie) {
        int values = 0;
        Iterator<Trie.IterationElement> iterator = unitrie.getInOrderIterator();
        while (iterator.hasNext()) {
            Trie.IterationElement iterationElement = iterator.next();
            if (iterationElement.getNode().getValue() != null) {
                values++;
            }
        }
        return values;
    }

    private static Map<ByteBuffer, byte[]> extractAllValues(Trie unitrie) {
        Map<ByteBuffer, byte[]> result = new HashMap<>();
        Iterator<Trie.IterationElement> iterator = unitrie.getInOrderIterator();
        while (iterator.hasNext()) {
            Trie.IterationElement iterationElement = iterator.next();
            Trie currentNode = iterationElement.getNode();
            if (currentNode.getValue() != null) {
                result.put(ByteBuffer.wrap(iterationElement.getNodeKey().encode()), currentNode.getValue());
                if (result.size() % 10_000 == 0) {
                    System.out.print('.');
                }
            }
        }
        System.out.println();
        return result;
    }

    private static Set<Keccak256> extractStorageHashes(Trie unitrie) {
        Set<Keccak256> keys = new HashSet<>();
        Iterator<Trie.IterationElement> iterator = unitrie.getInOrderIterator();
        while (iterator.hasNext()) {
            Trie.IterationElement iterationElement = iterator.next();
            TrieKeySlice nodeKey = iterationElement.getNodeKey();
            if (isContractStorageKey(nodeKey)) {
                keys.add(new Keccak256(iterationElement.getNode().getValueHash()));
            }
        }
        return keys;
    }


    private static Set<DataWord> extractStorageKeys(Trie unitrie) {
        Set<DataWord> keys = new HashSet<>();
        Iterator<Trie.IterationElement> iterator = unitrie.getInOrderIterator();
        while (iterator.hasNext()) {
            Trie.IterationElement iterationElement = iterator.next();
            TrieKeySlice nodeKey = iterationElement.getNodeKey();
            if (isContractStorageKey(nodeKey)) {
                keys.add(DataWord.valueOf(nodeKey.slice(nodeKey.length() - 32 * Byte.SIZE, nodeKey.length()).encode()));
            }
        }
        return keys;
    }

    private static Set<RskAddress> extractAccountsFromUnitrie(Trie unitrie) {
        Set<RskAddress> accounts = new HashSet<>();
        Iterator<Trie.IterationElement> iterator = unitrie.getInOrderIterator();
        while (iterator.hasNext()) {
            Trie.IterationElement iterationElement = iterator.next();
            TrieKeySlice nodeKey = iterationElement.getNodeKey();
            if (isAccount(nodeKey)) {
                accounts.add(new RskAddress(nodeKey.slice(nodeKey.length() - 20 * Byte.SIZE, nodeKey.length()).encode()));
            }
        }
        return accounts;
    }

    private static Set<Keccak256> extractAccountsHashesFromUnitrie(Trie unitrie) {
        Set<Keccak256> hashes = new HashSet<>();
        Iterator<Trie.IterationElement> iterator = unitrie.getInOrderIterator();
        while (iterator.hasNext()) {
            Trie.IterationElement iterationElement = iterator.next();
            TrieKeySlice nodeKey = iterationElement.getNodeKey();
            if (isAccount(nodeKey)) {
                hashes.add(new Keccak256(iterationElement.getNode().getValueHash()));
            }
        }
        return hashes;
    }


    private static boolean isAccount(TrieKeySlice key) {
        //boolean isRemascAccount = key.length() == (1 + MutableRepository.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length) * Byte.SIZE;
        return (key.length() == (1 + MutableRepository.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE);// || isRemascAccount);
    }

    private static boolean isContractStorageKey(TrieKeySlice key) {
        return key.length() == (1 + MutableRepository.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES + 1 + MutableRepository.SECURE_KEY_SIZE + DataWord.BYTES) * Byte.SIZE;
    }

    private static boolean isRemascStorageKey(TrieKeySlice key) {
        return key.length() == (1 + MutableRepository.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length + 1 + MutableRepository.SECURE_KEY_SIZE + DataWord.BYTES) * Byte.SIZE;
    }

    private static Trie getMigratedUnitrie() {
        TrieStoreImpl migratedUnitrieStore = new TrieStoreImpl(RskContext.makeDataSource("migrated-unitrie", "/Users/diegoll/Documents/databases/migration"));
        // wrongly migrated unitrie root for 399907
        return migratedUnitrieStore.retrieve(Hex.decode("e8bc8365a6f7b7effa60e32a9a112f7e984103b101503b7da3ce36e873b7b183"));
    }

    private static Trie getReplayedUnitrie() {
        TrieStoreImpl migratedUnitrieStore = new TrieStoreImpl(RskContext.makeDataSource("replayed-unitrie", "/Users/diegoll/Documents/databases/migration"));
        // wrongly migrated unitrie root for 399907
        return migratedUnitrieStore.retrieve(Hex.decode("03977e43480839d99686fc4264b0bff0a5a76eb83668820bc0ee2f1b1c717572"));
    }
}
