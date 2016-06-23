package br.inf.ufes.pp2016_01;

import java.rmi.RemoteException;
import java.io.BufferedReader;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.*;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MasterImpl implements Master {

    private static List<String> dictionary = new ArrayList<>();
    private Map<Integer, SlaveData> slaves = new HashMap<>();
    private Map<Integer, SlaveData> failed = new HashMap<>();
    private Map<String, Integer> slaveID = new HashMap<>();
    private List<Guess> guessList = new ArrayList<Guess>();

    private int nSlaves = 0;
    private int nFailed = 0;
    
    public MasterImpl(){
        MasterImpl.readDictionary();
    }

    //metodo de Attacker
    @Override
    public Guess[] attack(byte[] ciphertext, byte[] knowntext) {
        Guess[] guessArray = null;

        if(dictionary.size() > 0){
            System.out.println("Dicionário não foi lido corretamente.");
        }
        
        long tamVetor = dictionary.size();
        long tamVetorEscravos = tamVetor / slaves.size();
        long resto = tamVetor % slaves.size();
        long inicio = 0;
        long fim = tamVetorEscravos;

        for (Map.Entry<Integer, SlaveData> e : slaves.entrySet()) {
            //se a divisao de vetores nao for exata, vai atribuindo +1 campo do vetor a cada escravo
            if (resto > 0) {
                fim++;
                resto--;
            }

            SlaveData slaveData = e.getValue();
            Slave slave = slaveData.getSlaveReference();

            slaveData.setBeginIndex(inicio);
            slaveData.setEndIndex(fim);
            slaveData.setTime(System.nanoTime());

            try {
                slave.startSubAttack(ciphertext, knowntext, inicio, fim, this);
            } catch (RemoteException ex) {
                Logger.getLogger(MasterImpl.class.getName()).log(Level.SEVERE, null, ex);
            }

            inicio = fim;
            fim += tamVetorEscravos;
        }

        //esse return só pode acontecer quando a galera toda terminar
        
        return guessArray;
    }

    public static void readDictionary() {
        try {
            BufferedReader br;
            br = new BufferedReader(new FileReader("dictionary.txt"));

            //palavra lida
            String word;

            while ((word = br.readLine()) != null) {
                dictionary.add(word);
            }

            br.close();
        } catch (IOException ex) {
            System.out.println("Mestre: arquivo de dicionário não encontrado.");
        }
    }

    //metodos de SlaveManager
    @Override
    public synchronized int addSlave(Slave s, String slavename) throws RemoteException {

        if (slaveID.containsValue(slavename)) {
            System.out.println("Registro de escravo atualizado.");

            return slaveID.get(slavename);
        } else {
            SlaveData slaveData = new SlaveData(s, slavename, nSlaves);
            slaves.put(nSlaves, slaveData);
            slaveID.put(slavename, nSlaves);

            System.out.println("Escravo" + slavename + "adicionado.");

            return nSlaves++;
        }
    }

    @Override
    public synchronized void removeSlave(int slaveKey) throws RemoteException {
        if (slaves.containsValue(slaveKey)) {
            if (!slaves.get(slaveKey).isWorking()) {
                slaves.remove(slaveKey);
                slaveID.remove(slaves.get(slaveKey).getName());

                System.out.println("Escravo " + slaveKey + " removido.");
            } else {
                SlaveData s = slaves.get(slaveKey);
                SlaveData remaining = new SlaveData();
                remaining.setBeginIndex(s.getLastCheckedIndex());
                remaining.setEndIndex(s.getEndIndex());

                slaves.remove(slaveKey);
                slaveID.remove(s.getName());

                System.out.println("Escravo" + s.getName() + "removido.");
            }
        }
    }

    @Override
    public void foundGuess(long currentindex, Guess currentguess) throws RemoteException {
        System.out.println("Nova chave encontrada!");
        guessList.add(currentguess);
    }

    @Override
    public void checkpoint(long currentindex) throws RemoteException {
        //TODO
        //controlar os checkpoints de cada escravo e remove-los da fila em caso de falha
        //precisa criar uma thread que fique checando se cada escravo ultrapassou 20 continuamente
        //caso sim, retorna esse escravo pra ser removido

        //Procura em qual escravo esse checkpoint corresponde
        for (Map.Entry<Integer, SlaveData> entry : slaves.entrySet()) {
            if (currentindex >= entry.getValue().getBeginIndex() && currentindex <= entry.getValue().getEndIndex()) {
                System.out.println("Checkpoint Escravo: " + entry.getValue().getName());
                entry.getValue().setTime(System.nanoTime());
                entry.getValue().setLastCheckedIndex(currentindex);
            }
        }

    }

    // Captura o CTRL+C
    //http://stackoverflow.com/questions/1611931/catching-ctrlc-in-java
    public void attachShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {

                // Remove todos escravos da lista caso o mestre caia
                for (Map.Entry<Integer, SlaveData> slave : slaves
                        .entrySet()) {
                    slaves.remove(slave.getValue().getId());
                }
                System.out.println(" Mestre Caiu! :(");
            }
        });
    }

    public static void main(String[] args) throws Exception {

        try {
            //igual trab1
            String masterName = (args.length < 1) ? "mestre" : args[0];

            if (args.length > 0) {
                System.setProperty("java.rmi.server.hostname", args[0]);
            }

            Master master = new MasterImpl();
            
            Master stubRef = (Master) UnicastRemoteObject.exportObject(master, 2001);

            final Registry registry = LocateRegistry.getRegistry();
            registry.rebind(masterName, stubRef);
            
            System.out.println("Mestre está pronto...");

            //TODO
            //lembrar de fazer o attachShutDownHook
        } catch (Exception e) {
            System.err.println("Mestre gerou exceção.");
        }
    }
}
