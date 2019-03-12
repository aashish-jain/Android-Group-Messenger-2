package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    TextView textView;
    EditText editText;
    int selfProcessId, messageId=0;
    Uri uri;
    AtomicInteger contentProviderKey;
    Long proposalNumber;
    PriorityQueue<Message> deliveryQueue;
    LinkedBlockingQueue<Message> proposalQueue;
    int idIncrementValue = 0;

    final int selfProcessIdLen = 4, initCapacity = 100;

    Map<Integer, ClientThread> socketMap = new HashMap<Integer, ClientThread>();

    private int getProcessId(){
        String telephoneNumber =
                ((TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
        int length = telephoneNumber.length();
        telephoneNumber = telephoneNumber.substring(length - selfProcessIdLen);
        int id = Integer.parseInt(telephoneNumber);
        return id;
    }

    private void writeToContentProvider(int key, String message){
        ContentValues contentValues = new ContentValues();
        contentValues.put("key", new Integer(key).toString());
        contentValues.put("value", message);
        getContentResolver().insert(uri, contentValues);
    }

    public String printPriorityQueue(PriorityQueue<Message> priorityQueue) {
        StringBuilder stringBuilder = new StringBuilder();
        PriorityQueue<Message> pq = new PriorityQueue<Message>(priorityQueue);
        int i = pq.size();
        for (; i!=0; i--){
            stringBuilder.append( pq.poll().toString() + "\n" );
        }
        return stringBuilder.toString();
    }

    public Message findInPriorityQueue(PriorityQueue<Message> priorityQueue, Message message) {
        Message toReturn = null;
        for (Message priorityQueueIterator : priorityQueue)
            if (priorityQueueIterator.getId() == message.getId()) {
                toReturn = message;
                break;
            }
        return toReturn;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /* Get the process ID using telephone number*/
        selfProcessId = getProcessId();

        /* Content Provider Key*/
        contentProviderKey = new AtomicInteger(0);

        /* Proposal Number*/
        proposalNumber = new Long(selfProcessId);

        /* Id increment value*/
        idIncrementValue = (int) Math.pow(10, selfProcessIdLen);

        /* delivery Queue */
        deliveryQueue = new PriorityQueue<Message>(initCapacity, new MessageComparator());

        /* Proposal Queue */
        proposalQueue = new LinkedBlockingQueue<Message>(initCapacity);

        /* Uri for the content provider */
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        uri = uriBuilder.build();

        /* Starts the Server Interactor Spawner */
        Log.d("UI", "Server Started");
        new ServerThreadSpawner(selfProcessId).start();


        /* Process Id generator */
        messageId = selfProcessId;

        /* Get the textView and the editText of the activity */
        textView = (TextView) findViewById(R.id.textView1);
        textView.setMovementMethod(new ScrollingMovementMethod());
        editText = (EditText) findViewById(R.id.editText1);
        textView = (TextView) findViewById(R.id.textView1);

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(textView, getContentResolver()));

        /* https://developer.android.com/reference/android/widget/Button */
        Button button = (Button) findViewById(R.id.button4);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = editText.getText().toString() + "\n";

                /* Don't send empty messages */
                if(message.equals("\n") || message.equals(" \n"))
                    return;
                editText.setText("");

                //, message, messageId
                /* Update the message id here as it is threadsafe.
                * Last 4 digits are process id and message/1000 is the message number
                * */

                new ClientThreadSpawner().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, new Message(messageId, message));
                //Update the Id
                messageId+= idIncrementValue;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    //https://stackoverflow.com/questions/5853167/runnable-with-a-parameter/5853198
    private class UpdateTextView implements Runnable{
        String message;
        int from;


        UpdateTextView(String message, int from){
            this.message = message;
            this.from = from;
        }

        /* https://stackoverflow.com/questions/8105545/can-i-select-a-color-for-each-text-line-i-append-to-a-textview*/
        public void appendColoredText(TextView tv, String text, int color) {
            int start = tv.getText().length();
            tv.append(text);
            int end = tv.getText().length();

            Spannable spannableText = (Spannable) tv.getText();
            spannableText.setSpan(new ForegroundColorSpan(color), start, end, 0);
        }

        @Override
        public synchronized void run() {
            int color = 0;
            switch (from){
                case 5554:  color = Color.MAGENTA;  break;
                case 5556:  color = Color.BLACK;    break;
                case 5558:  color = Color.DKGRAY;    break;
                case 5560:  color = Color.BLUE;     break;
                case 5562:  color = Color.RED;      break;
                default:    color = Color.BLACK;   break;
            }
            String line = "("+from + ") : " + message;
            appendColoredText(textView, line, color);
        }
    }

    //https://stackoverflow.com/questions/10131377/socket-programming-multiple-client-to-one-server
    private class ServerThreadSpawner extends Thread {
        static final int SERVER_PORT = 10000;
        static final String TAG = "SERVER_TASK";
        int selfProcessId;


        public ServerThreadSpawner(int selfProcessId){
            this.selfProcessId = selfProcessId;
            Log.d(TAG, "Got selfProcessId = " + selfProcessId);
        }

        public void run() {
            /* Open a socket at SERVER_PORT */
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }

            /* Accept a client connection and spawn a thread to respond */
            while (true)
                try {
                    Socket socket = serverSocket.accept();
                    new ServerThread(socket, this.selfProcessId).start();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "ServerThreadSpawner socket IOException");
                }
        }
    }


    /* https://stackoverflow.com/questions/10131377/socket-programming-multiple-client-to-one-server*/
    private class ServerThread extends Thread {
        ObjectOutputStream oos;
        ObjectInputStream ois;
        int selfId, clientProcessId;

        static final String TAG = "SERVER_THREAD";


        public  ServerThread(Socket socket, int selfId) {
            try {
                this.ois = new ObjectInputStream(socket.getInputStream());
                this.oos = new ObjectOutputStream(socket.getOutputStream());
                this.clientProcessId = ois.readInt();
                this.selfId = selfId;
            } catch (IOException e) {
                Log.e(TAG, "Unable to connect");
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            //Read from the socket
            try {
                while (true) {
                    Message message = new Message(ois.readUTF());
                    Log.d(TAG, "Recieved " + message.toString());
                    /* https://stackoverflow.com/questions/12716850/android-update-textview-in-thread-and-runnable
                     * Update the UI thread */
                    runOnUiThread(new UpdateTextView(message.getMessage(), clientProcessId));

                    /*
                    long proposal = 0;
                    synchronized (proposalNumber){
                        proposal = proposalNumber;
                        proposalNumber+=1;
                    }
                    oos.writeLong(proposal);
                    */
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClientThreadSpawner extends AsyncTask<Message, Void, Void> {
        final String TAG = "CLIENT_THREAD";
        final int[] remoteProcessIds = new int[]{11108, 11112, 11116, 11120, 11124};


        protected void establishConnection(){
                /* Establish the connection to server and store it in a Hashmap*/
                for (int remoteProcessId : remoteProcessIds) {
                    Socket socket = null;
                    try {
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                remoteProcessId);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ClientThread clientThread = new ClientThread(remoteProcessId, socket);
                    socketMap.put(remoteProcessId, clientThread);
                    clientThread.start();
                }
            }

            @Override
            protected Void doInBackground(Message... msgs) {
                Message message = msgs[0];

                //For the first time attempt to establish connection
                if(socketMap.size() == 0)
                    establishConnection();

                Log.d(TAG, message.toString());
                for(int remoteProcessId : socketMap.keySet())
                    socketMap.get(remoteProcessId).addToQueue(message);
                return null;
        }
    }

    private class ProposalHandler extends Thread{
        final String TAG = "PROPOSAL_HANDLER";

        ProposalHandler(){
            this.start();
        }

        @Override
        public void run(){
            try {
                /* Take the message from the proposal queue */
                Message message = proposalQueue.take();

                /* Find the message with given Id, update it and re-insert to the queue*/
                Message queueMessage = findInPriorityQueue(deliveryQueue, message);
                deliveryQueue.remove(queueMessage);
                queueMessage.setPriority(message.getPriority());
                deliveryQueue.offer(queueMessage);

                /* Get the first message from the delivery queue and check if it is deliverable
                * Then deliver all the deliverable messages at the beginning of the queue
                */
                message = deliveryQueue.peek();
                while (message.isDeliverable()){
                    /** TODO: Deliver the message */
                    Log.d(TAG, "Delivered "+ message.toString());
                    deliveryQueue.poll();
                    writeToContentProvider(contentProviderKey.getAndIncrement(), message.getMessage());
                    message = deliveryQueue.peek();
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    private class ClientThread extends Thread{
        Socket socket;
        int remoteProcessId;
        ObjectOutputStream oos;
        ObjectInputStream ois;
        LinkedBlockingQueue<Message> messageQueue;
        final String TAG = "CLIENT_THREAD";

        ClientThread(int remoteProcessId, Socket socket){
            this.messageQueue = new LinkedBlockingQueue<Message>();
            this.socket = socket;
            this.remoteProcessId = remoteProcessId;
            try {
                /*
                 * https://stackoverflow.com/questions/5680259/using-sockets-to-send-and-receive-data
                 * https://stackoverflow.com/questions/49654735/send-objects-and-strings-over-socket
                 */
                /* Streams */
                this.oos = new ObjectOutputStream(socket.getOutputStream());
                this.ois = new ObjectInputStream(socket.getInputStream());
                /* One-time operation. Send server the client's process id*/
                this.oos.writeInt(selfProcessId);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void addToQueue(Message message){
            try {
                messageQueue.put(message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run(){
            while (true){
                try {
                    Message message = messageQueue.take();
                    Log.d(TAG, message.getId() + ':' + message.getMessage());
                    try {
                        oos.writeUTF(message.encodeMessage());
                        oos.flush();
                        /*
                        long proposal = this.ois.readLong();
                        */

                        /* Update the global proposal number */
                        /*
                        synchronized(proposalNumber){
                            if(proposalNumber < proposal)
                                proposalNumber = proposal;
                        }
                        */

                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientThread UnknownHostException port=" + remoteProcessId);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "ClientThread socket IOException");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
