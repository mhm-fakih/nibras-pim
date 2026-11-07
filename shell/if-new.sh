if [ -d $HOME/new ] && [ ! -L $HOME/new ] && [ -w $HOME/new ];  then 
echo "🟢 NEW" 
else
 echo "🔴 NEW"
fi
